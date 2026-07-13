package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.OutboxEvent;

import java.util.List;

public interface IOutboxEventService extends IService<OutboxEvent> {

    void createEvent(String aggregateType, Long aggregateId, String eventType, String topic, String payload);

    List<OutboxEvent> queryPendingEvents(int limit);

    void markSent(Long eventId);

    void markFailed(Long eventId, String errorMessage);

    void dispatchPendingEvents();
}
