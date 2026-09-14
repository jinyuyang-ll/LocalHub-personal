package com.hmdp.service.impl;

import com.hmdp.entity.OutboxEvent;
import com.hmdp.config.LocalHubMetrics;
import com.hmdp.service.IOutboxEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "localhub.kafka", name = "enabled", havingValue = "true")
public class KafkaOutboxDispatcher {

    private final String workerId = UUID.randomUUID().toString();

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
        List<OutboxEvent> events = outboxEventService.claimPendingEvents(50, workerId,
                LocalDateTime.now().plusSeconds(30));
        for (OutboxEvent event : events) {
            try {
                kafkaTemplate.send(
                        event.getTopic(),
                        event.getEventId(),
                        event.getPayload()
                ).get(10, java.util.concurrent.TimeUnit.SECONDS);
                outboxEventService.markSent(event.getId(), workerId);
                metrics.increment("outbox.dispatch", "success");
                log.info("Outbox event dispatched. id={}, topic={}, type={}",
                        event.getId(), event.getTopic(), event.getEventType());
            } catch (Exception e) {
                outboxEventService.markFailed(event.getId(), workerId, e.getMessage());
                metrics.increment("outbox.dispatch", "failed");
                log.warn("Outbox event dispatch failed. id={}, topic={}", event.getId(), event.getTopic(), e);
            }
        }
    }
}
