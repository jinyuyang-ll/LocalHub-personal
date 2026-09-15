package com.hmdp.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.*;

class OrderEventConsumerTest {
    @Test void duplicateEventIdDoesNotRepeatBusinessMutation() {
        OrderReleaseService release = mock(OrderReleaseService.class);
        ConsumerMessageService messages = mock(ConsumerMessageService.class);
        when(messages.tryRecord("order-release", "event-1", "VOUCHER_ORDER_CANCELLED"))
                .thenReturn(true, false);
        OrderEventConsumer consumer = new OrderEventConsumer();
        ReflectionTestUtils.setField(consumer, "orderReleaseService", release);
        ReflectionTestUtils.setField(consumer, "consumerMessageService", messages);
        String envelope = "{\"eventId\":\"event-1\",\"eventType\":\"VOUCHER_ORDER_CANCELLED\"," +
                "\"aggregateId\":100,\"occurredAt\":\"2026-01-01T00:00:00\"," +
                "\"data\":{\"orderId\":100,\"userId\":7,\"voucherId\":9,\"status\":4}}";

        consumer.consume(envelope);
        consumer.consume(envelope);

        verify(release, times(1)).release(100L, 7L, 9L, "ORDER_CANCELLED_OR_CLOSED");
    }
}
