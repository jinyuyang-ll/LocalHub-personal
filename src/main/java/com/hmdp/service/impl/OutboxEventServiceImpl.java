package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.OutboxEvent;
import com.hmdp.mapper.OutboxEventMapper;
import com.hmdp.service.IOutboxEventService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxEventServiceImpl extends ServiceImpl<OutboxEventMapper, OutboxEvent> implements IOutboxEventService {

    private static final int STATUS_NEW = 0;
    private static final int STATUS_SENT = 1;
    private static final int STATUS_FAILED = 2;
    private static final int STATUS_PROCESSING = 3;

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
    @Transactional
    public List<OutboxEvent> claimPendingEvents(int limit, String workerId, LocalDateTime lockedUntil) {
        List<OutboxEvent> candidates = baseMapper.selectClaimable(Math.max(1, Math.min(limit, 200)));
        java.util.ArrayList<OutboxEvent> claimed = new java.util.ArrayList<>(candidates.size());
        for (OutboxEvent event : candidates) {
            boolean updated = lambdaUpdate()
                    .eq(OutboxEvent::getId, event.getId())
                    .and(wrapper -> wrapper
                            .in(OutboxEvent::getStatus, STATUS_NEW, STATUS_FAILED)
                            .or(nested -> nested.eq(OutboxEvent::getStatus, STATUS_PROCESSING)
                                    .le(OutboxEvent::getLockedUntil, LocalDateTime.now())))
                    .set(OutboxEvent::getStatus, STATUS_PROCESSING)
                    .set(OutboxEvent::getLockedBy, workerId)
                    .set(OutboxEvent::getLockedUntil, lockedUntil)
                    .set(OutboxEvent::getVersion, event.getVersion() + 1)
                    .update();
            if (updated) {
                event.setStatus(STATUS_PROCESSING);
                event.setLockedBy(workerId);
                event.setLockedUntil(lockedUntil);
                event.setVersion(event.getVersion() + 1);
                claimed.add(event);
            }
        }
        return claimed;
    }

    @Override
    public void markSent(Long eventId, String workerId) {
        OutboxEvent event = getById(eventId);
        if (event == null || event.getStatus() == STATUS_SENT) return;
        lambdaUpdate()
                .eq(OutboxEvent::getId, eventId)
                .eq(OutboxEvent::getStatus, STATUS_PROCESSING)
                .eq(OutboxEvent::getLockedBy, workerId)
                .eq(OutboxEvent::getVersion, event.getVersion())
                .set(OutboxEvent::getStatus, STATUS_SENT)
                .set(OutboxEvent::getLastError, null)
                .set(OutboxEvent::getSentTime, LocalDateTime.now())
                .set(OutboxEvent::getLockedBy, null)
                .set(OutboxEvent::getLockedUntil, null)
                .set(OutboxEvent::getVersion, event.getVersion() + 1)
                .update();
    }

    @Override
    public void markFailed(Long eventId, String workerId, String errorMessage) {
        OutboxEvent event = getById(eventId);
        if (event == null) {
            return;
        }
        int retryCount = event.getRetryCount() == null ? 1 : event.getRetryCount() + 1;
        int delayMinutes = Math.min(retryCount, 10);
        lambdaUpdate()
                .eq(OutboxEvent::getId, eventId)
                .eq(OutboxEvent::getStatus, STATUS_PROCESSING)
                .eq(OutboxEvent::getLockedBy, workerId)
                .eq(OutboxEvent::getVersion, event.getVersion())
                .set(OutboxEvent::getStatus, STATUS_FAILED)
                .set(OutboxEvent::getRetryCount, retryCount)
                .set(OutboxEvent::getNextRetryTime, LocalDateTime.now().plusMinutes(delayMinutes))
                .set(OutboxEvent::getLastError, trimError(errorMessage))
                .set(OutboxEvent::getLockedBy, null)
                .set(OutboxEvent::getLockedUntil, null)
                .set(OutboxEvent::getVersion, event.getVersion() + 1)
                .update();
    }

    @Override
    public long pendingCount() {
        return baseMapper.countPending();
    }

    @Override
    public long oldestPendingAgeSeconds() {
        LocalDateTime oldest = baseMapper.oldestPendingTime();
        return oldest == null ? 0 : Math.max(0, java.time.Duration.between(oldest, LocalDateTime.now()).getSeconds());
    }

    private String trimError(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        return errorMessage.length() > 512 ? errorMessage.substring(0, 512) : errorMessage;
    }
}
