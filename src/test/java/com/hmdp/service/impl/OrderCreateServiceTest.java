package com.hmdp.service.impl;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.enums.SeckillOrderState;
import com.hmdp.mapper.VoucherOrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.*;

class OrderCreateServiceTest {
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
