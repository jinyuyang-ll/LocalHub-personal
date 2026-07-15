package com.hmdp.service.impl;

import com.hmdp.config.LocalHubMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KafkaSeckillOrderConsumerTest {

    @Mock private VoucherOrderServiceImpl voucherOrderService;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private LocalHubMetrics metrics;
    @InjectMocks private KafkaSeckillOrderConsumer consumer;

    @BeforeEach
    void configure() {
        ReflectionTestUtils.setField(consumer, "retryTopic", "retry-topic");
        ReflectionTestUtils.setField(consumer, "dltTopic", "dlt-topic");
        ReflectionTestUtils.setField(consumer, "maxAttempts", 3);
    }

    @Test
    void failedFirstAttemptIsPublishedToRetryTopic() {
        doThrow(new IllegalStateException("database unavailable"))
                .when(voucherOrderService).createVoucherOrder(org.mockito.ArgumentMatchers.any());
        String payload = "{\"orderId\":1,\"userId\":2,\"voucherId\":3,\"retryCount\":0}";
        assertThrows(IllegalStateException.class, () -> consumer.consume(payload));
        verify(kafkaTemplate).send(eq("retry-topic"), eq("1"), anyString());
        verify(metrics).increment("seckill.consumer", "failed");
    }

    @Test
    void exhaustedAttemptIsPublishedToDeadLetterTopic() {
        doThrow(new IllegalStateException("database unavailable"))
                .when(voucherOrderService).createVoucherOrder(org.mockito.ArgumentMatchers.any());
        String payload = "{\"orderId\":1,\"userId\":2,\"voucherId\":3,\"retryCount\":2}";
        assertThrows(IllegalStateException.class, () -> consumer.consumeRetry(payload));
        verify(kafkaTemplate).send(eq("dlt-topic"), eq("1"), anyString());
        verify(metrics).increment("kafka.dlt", "published");
    }
}
