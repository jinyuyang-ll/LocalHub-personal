package com.hmdp.service.impl;

import com.hmdp.entity.OutboxEvent;
import com.hmdp.service.IOutboxEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "localhub.kafka", name = "enabled", havingValue = "true")
public class KafkaOutboxDispatcher {

    @Resource
    private IOutboxEventService outboxEventService;

    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 5_000)
    public void dispatch() {
        List<OutboxEvent> events = outboxEventService.queryPendingEvents(50);
        for (OutboxEvent event : events) {
            try {
                kafkaTemplate.send(
                        event.getTopic(),
                        String.valueOf(event.getAggregateId()),
                        event.getPayload()
                ).get();
                outboxEventService.markSent(event.getId());
                log.info("Outbox event dispatched. id={}, topic={}, type={}",
                        event.getId(), event.getTopic(), event.getEventType());
            } catch (Exception e) {
                outboxEventService.markFailed(event.getId(), e.getMessage());
                log.warn("Outbox event dispatch failed. id={}, topic={}", event.getId(), event.getTopic(), e);
            }
        }
    }
}
