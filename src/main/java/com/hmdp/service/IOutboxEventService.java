package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.OutboxEvent;

import java.util.List;

public interface IOutboxEventService extends IService<OutboxEvent> {

    void createEvent(String aggregateType, Long aggregateId, String eventType, String topic, String payload);

    List<OutboxEvent> claimPendingEvents(int limit, String workerId, java.time.LocalDateTime lockedUntil);

    void markSent(Long eventId, String workerId);

    void markFailed(Long eventId, String workerId, String errorMessage);

    long pendingCount();

    long oldestPendingAgeSeconds();

}
