package com.hmdp.service.impl;

import com.hmdp.enums.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderStatusTest {
    @Test void onlyUnpaidOrderCanBePaidOrCancelled() {
        assertTrue(OrderStatus.UNPAID.canTransitionTo(OrderStatus.PAID));
        assertTrue(OrderStatus.UNPAID.canTransitionTo(OrderStatus.CANCELLED));
        assertFalse(OrderStatus.PAID.canTransitionTo(OrderStatus.CANCELLED));
        assertFalse(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.PAID));
    }
}
