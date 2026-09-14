package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoucherOrderServiceTest {
    private final SeckillService seckill = mock(SeckillService.class);
    private final OrderStatusService status = mock(OrderStatusService.class);
    private final OrderPaymentService payment = mock(OrderPaymentService.class);
    private final OrderCancelService cancel = mock(OrderCancelService.class);
    private final OrderCloseService close = mock(OrderCloseService.class);
    private final VoucherOrderServiceImpl facade = new VoucherOrderServiceImpl();

    @BeforeEach void setUp() {
        ReflectionTestUtils.setField(facade, "seckillService", seckill);
        ReflectionTestUtils.setField(facade, "orderStatusService", status);
        ReflectionTestUtils.setField(facade, "orderPaymentService", payment);
        ReflectionTestUtils.setField(facade, "orderCancelService", cancel);
        ReflectionTestUtils.setField(facade, "orderCloseService", close);
    }

    @Test void delegatesSeckillToDedicatedService() {
        Result expected = Result.ok(99L);
        when(seckill.seckill(1L)).thenReturn(expected);
        assertSame(expected, facade.seckillVoucher(1L));
        verify(seckill).seckill(1L);
    }

    @Test void delegatesPaymentAndCancellation() {
        facade.payOrder(10L); facade.cancelOrder(10L);
        verify(payment).pay(10L); verify(cancel).cancel(10L);
    }
}
