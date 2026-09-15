package com.hmdp.service.impl;

import com.hmdp.entity.OutboxEvent;
import com.hmdp.config.LocalHubMetrics;
import com.hmdp.dto.EventEnvelope;
import com.hmdp.service.IOutboxEventService;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.Resource;
import java.util.List;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "localhub.kafka", name = "enabled", havingValue = "true")
public class KafkaOutboxDispatcher {

    /**
     * tb_outbox_event.locked_by is varchar(64). Keep a short process prefix for
     * diagnostics and append a fresh compact UUID for fencing each claim batch.
     */
    private final String workerId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    @Value("${localhub.outbox.batch-size:10}")
    private int batchSize = 10;

    @Value("${localhub.outbox.lease-seconds:120}")
    private long leaseSeconds = 120;

    @Resource
    private IOutboxEventService outboxEventService;

    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;
    @Resource
    private LocalHubMetrics metrics;

    @Scheduled(fixedDelay = 5_000)
    public void dispatch() {
        metrics.setGauge("outbox.pending", outboxEventService.pendingCount());
        metrics.setGauge("outbox.oldest.age.seconds", outboxEventService.oldestPendingAgeSeconds());
        String claimOwner = workerId + ":" + UUID.randomUUID().toString().replace("-", "");
        List<OutboxEvent> events = outboxEventService.claimPendingEvents(
                Math.max(1, Math.min(batchSize, 20)), claimOwner,
                LocalDateTime.now().plusSeconds(Math.max(30, leaseSeconds)));
        for (OutboxEvent event : events) {
            try {
                kafkaTemplate.send(
                        event.getTopic(),
                        event.getEventId(),
                        envelope(event)
                ).get(10, java.util.concurrent.TimeUnit.SECONDS);
                outboxEventService.markSent(event.getId(), claimOwner);
                metrics.increment("outbox.dispatch", "success");
                log.info("Outbox event dispatched. id={}, topic={}, type={}",
                        event.getId(), event.getTopic(), event.getEventType());
            } catch (Exception e) {
                outboxEventService.markFailed(event.getId(), claimOwner, e.getMessage());
                metrics.increment("outbox.dispatch", "failed");
                log.warn("Outbox event dispatch failed. id={}, topic={}", event.getId(), event.getTopic(), e);
            }
        }
    }

    private String envelope(OutboxEvent event) {
        Object data;
        try {
            data = JSONUtil.parse(event.getPayload());
        } catch (RuntimeException invalidJson) {
            data = event.getPayload();
        }
        EventEnvelope envelope = new EventEnvelope(event.getEventId(), event.getEventType(),
                event.getAggregateId(), event.getCreateTime() == null
                ? LocalDateTime.now().toString() : event.getCreateTime().toString(), data);
        return JSONUtil.toJsonStr(envelope);
    }
}
