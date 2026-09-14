package com.hmdp.service.impl;

import com.hmdp.config.LocalHubMetrics;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.concurrent.SettableListenableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SeckillServiceTest {

    @AfterEach void clearUser() {
        UserHolder.removeUser();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test void kafkaSendDoesNotBlockHttpThread() {
        RedisIdWorker idWorker = mock(RedisIdWorker.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        ObjectProvider<SeckillKafkaRecoveryService> recovery = mock(ObjectProvider.class);
        SeckillCompensationService compensation = mock(SeckillCompensationService.class);
        LocalHubMetrics metrics = mock(LocalHubMetrics.class);
        when(idWorker.nextId("order")).thenReturn(1001L);
        when(redis.execute(any(DefaultRedisScript.class), anyList(),
                anyString(), anyString(), anyString(), anyString())).thenReturn(0L);
        SettableListenableFuture neverCompleted = new SettableListenableFuture();
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(neverCompleted);

        SeckillService service = new SeckillService();
        ReflectionTestUtils.setField(service, "idWorker", idWorker);
        ReflectionTestUtils.setField(service, "redis", redis);
        ReflectionTestUtils.setField(service, "kafkaTemplate", kafka);
        ReflectionTestUtils.setField(service, "recoveryProvider", recovery);
        ReflectionTestUtils.setField(service, "compensationService", compensation);
        ReflectionTestUtils.setField(service, "metrics", metrics);
        ReflectionTestUtils.setField(service, "queue", "kafka");
        ReflectionTestUtils.setField(service, "ordersTopic", "orders");
        UserDTO user = new UserDTO();
        user.setId(7L);
        UserHolder.saveUser(user);

        long started = System.nanoTime();
        Result result = service.seckill(9L);
        long elapsedMillis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertTrue(result.getSuccess());
        assertEquals(1001L, result.getData());
        assertTrue(elapsedMillis < 500, "HTTP path must not wait for the Kafka acknowledgement");
        verify(kafka).send(eq("orders"), eq("1001"), anyString());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test void asynchronousKafkaFailureEntersDurableRecovery() {
        RedisIdWorker idWorker = mock(RedisIdWorker.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        ObjectProvider<SeckillKafkaRecoveryService> recoveryProvider = mock(ObjectProvider.class);
        SeckillKafkaRecoveryService recovery = mock(SeckillKafkaRecoveryService.class);
        SeckillCompensationService compensation = mock(SeckillCompensationService.class);
        LocalHubMetrics metrics = mock(LocalHubMetrics.class);
        when(idWorker.nextId("order")).thenReturn(1002L);
        when(redis.execute(any(DefaultRedisScript.class), anyList(),
                anyString(), anyString(), anyString(), anyString())).thenReturn(0L);
        SettableListenableFuture failedLater = new SettableListenableFuture();
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(failedLater);
        when(recoveryProvider.getIfAvailable()).thenReturn(recovery);

        SeckillService service = new SeckillService();
        ReflectionTestUtils.setField(service, "idWorker", idWorker);
        ReflectionTestUtils.setField(service, "redis", redis);
        ReflectionTestUtils.setField(service, "kafkaTemplate", kafka);
        ReflectionTestUtils.setField(service, "recoveryProvider", recoveryProvider);
        ReflectionTestUtils.setField(service, "compensationService", compensation);
        ReflectionTestUtils.setField(service, "metrics", metrics);
        ReflectionTestUtils.setField(service, "queue", "kafka");
        ReflectionTestUtils.setField(service, "ordersTopic", "orders");
        UserDTO user = new UserDTO();
        user.setId(8L);
        UserHolder.saveUser(user);

        assertTrue(service.seckill(10L).getSuccess());
        RuntimeException brokerDown = new RuntimeException("broker unavailable");
        failedLater.setException(brokerDown);

        verify(recovery).schedulePublish(argThat(message -> message.getOrderId().equals(1002L)), same(brokerDown));
        verify(compensation, never()).compensateFailure(any(), anyString());
    }
}
