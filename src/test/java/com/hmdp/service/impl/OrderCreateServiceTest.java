package com.hmdp.service.impl;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.enums.SeckillOrderState;
import com.hmdp.mapper.VoucherOrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import com.hmdp.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.Mockito.*;

class OrderCreateServiceTest {
    @Test void lockUsesUserAndVoucherGranularity() {
        VoucherOrderMapper mapper = mock(VoucherOrderMapper.class);
        RedissonClient redisson = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(redisson.getLock("lock:order:7:9")).thenReturn(lock);
        when(lock.tryLock()).thenReturn(false);
        VoucherOrder order = new VoucherOrder();
        order.setId(101L); order.setUserId(7L); order.setVoucherId(9L);
        OrderCreateService service = new OrderCreateService();
        ReflectionTestUtils.setField(service, "orderMapper", mapper);
        ReflectionTestUtils.setField(service, "redissonClient", redisson);

        assertThrows(BusinessException.class, () -> service.createOrder(order));
        verify(redisson).getLock("lock:order:7:9");
    }

    @Test void duplicateKafkaDeliveryUsesOrderIdAsIdempotencyKey() {
        VoucherOrderMapper mapper = mock(VoucherOrderMapper.class);
        OrderStatusService status = mock(OrderStatusService.class);
        SeckillKafkaRecoveryService recovery = mock(SeckillKafkaRecoveryService.class);
        ObjectProvider<SeckillKafkaRecoveryService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(recovery);
        VoucherOrder existing = new VoucherOrder();
        existing.setId(100L); existing.setUserId(7L); existing.setVoucherId(9L);
        when(mapper.selectById(100L)).thenReturn(existing);

        OrderCreateService service = new OrderCreateService();
        ReflectionTestUtils.setField(service, "orderMapper", mapper);
        ReflectionTestUtils.setField(service, "orderStatusService", status);
        ReflectionTestUtils.setField(service, "recoveryProvider", provider);
        service.createOrder(existing);

        verify(mapper, never()).insert(any());
        verify(status).transition(100L, SeckillOrderState.SUCCESS);
        verify(recovery).complete(100L);
    }
}
