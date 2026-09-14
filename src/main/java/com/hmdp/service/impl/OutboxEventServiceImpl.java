package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.OutboxEvent;
import com.hmdp.mapper.OutboxEventMapper;
import com.hmdp.service.IOutboxEventService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxEventServiceImpl extends ServiceImpl<OutboxEventMapper, OutboxEvent> implements IOutboxEventService {

    private static final int STATUS_NEW = 0;
    private static final int STATUS_SENT = 1;
    private static final int STATUS_FAILED = 2;

    @Override
    public void createEvent(String aggregateType, Long aggregateId, String eventType, String topic, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setTopic(topic);
        event.setPayload(payload);
        event.setStatus(STATUS_NEW);
        event.setRetryCount(0);
        event.setNextRetryTime(LocalDateTime.now());
        event.setVersion(0L);
        save(event);
    }

    @Override
    public List<OutboxEvent> queryPendingEvents(int limit) {
        LocalDateTime now = LocalDateTime.now();
        return lambdaQuery()
                .in(OutboxEvent::getStatus, STATUS_NEW, STATUS_FAILED)
                .le(OutboxEvent::getNextRetryTime, now)
                .orderByAsc(OutboxEvent::getCreateTime)
                .page(new Page<>(1, limit))
                .getRecords();
    }

    @Override
    public void markSent(Long eventId) {
        OutboxEvent event = getById(eventId);
        if (event == null || event.getStatus() == STATUS_SENT) return;
        lambdaUpdate()
                .eq(OutboxEvent::getId, eventId)
                .eq(OutboxEvent::getVersion, event.getVersion())
                .set(OutboxEvent::getStatus, STATUS_SENT)
                .set(OutboxEvent::getLastError, null)
                .set(OutboxEvent::getSentTime, LocalDateTime.now())
                .set(OutboxEvent::getVersion, event.getVersion() + 1)
                .update();
    }

    @Override
    public void markFailed(Long eventId, String errorMessage) {
        OutboxEvent event = getById(eventId);
        if (event == null) {
            return;
        }
        int retryCount = event.getRetryCount() == null ? 1 : event.getRetryCount() + 1;
        int delayMinutes = Math.min(retryCount, 10);
        lambdaUpdate()
                .eq(OutboxEvent::getId, eventId)
                .eq(OutboxEvent::getVersion, event.getVersion())
                .set(OutboxEvent::getStatus, STATUS_FAILED)
                .set(OutboxEvent::getRetryCount, retryCount)
                .set(OutboxEvent::getNextRetryTime, LocalDateTime.now().plusMinutes(delayMinutes))
                .set(OutboxEvent::getLastError, trimError(errorMessage))
                .set(OutboxEvent::getVersion, event.getVersion() + 1)
                .update();
    }

    private String trimError(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        return errorMessage.length() > 512 ? errorMessage.substring(0, 512) : errorMessage;
    }
}
