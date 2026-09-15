package com.hmdp.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.config.LocalHubMetrics;
import com.hmdp.entity.OutboxEvent;
import com.hmdp.service.IOutboxEventService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.concurrent.SettableListenableFuture;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KafkaOutboxDispatcherTest {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test void publishesStableEventEnvelope() {
        IOutboxEventService outbox = mock(IOutboxEventService.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        LocalHubMetrics metrics = mock(LocalHubMetrics.class);
        OutboxEvent event = new OutboxEvent();
        event.setId(1L);
        event.setEventId("event-1");
        event.setEventType("VOUCHER_ORDER_CANCELLED");
        event.setAggregateId(99L);
        event.setTopic("orders");
        event.setPayload("{\"orderId\":99}");
        event.setCreateTime(LocalDateTime.of(2026, 1, 2, 3, 4));
        when(outbox.claimPendingEvents(eq(10), anyString(), any())).thenReturn(Collections.singletonList(event));
        SettableListenableFuture future = new SettableListenableFuture();
        future.set(null);
        when(kafka.send(eq("orders"), eq("event-1"), anyString())).thenReturn(future);

        KafkaOutboxDispatcher dispatcher = new KafkaOutboxDispatcher();
        ReflectionTestUtils.setField(dispatcher, "outboxEventService", outbox);
        ReflectionTestUtils.setField(dispatcher, "kafkaTemplate", kafka);
        ReflectionTestUtils.setField(dispatcher, "metrics", metrics);
        dispatcher.dispatch();

        org.mockito.ArgumentCaptor<String> payload = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq("orders"), eq("event-1"), payload.capture());
        org.mockito.ArgumentCaptor<String> claimOwner = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(outbox).claimPendingEvents(eq(10), claimOwner.capture(), any());
        assertTrue(claimOwner.getValue().length() <= 64,
                "claim owner must fit tb_outbox_event.locked_by varchar(64)");
        verify(outbox).markSent(1L, claimOwner.getValue());
        JSONObject envelope = JSONUtil.parseObj(payload.getValue());
        assertEquals("event-1", envelope.getStr("eventId"));
        assertEquals("VOUCHER_ORDER_CANCELLED", envelope.getStr("eventType"));
        assertEquals(99L, envelope.getLong("aggregateId"));
        assertEquals(99L, envelope.getJSONObject("data").getLong("orderId"));
    }
}
