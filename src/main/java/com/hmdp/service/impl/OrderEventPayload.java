package com.hmdp.service.impl;

import com.hmdp.entity.VoucherOrder;

final class OrderEventPayload {
    private OrderEventPayload() { }

    static String from(VoucherOrder order) {
        return String.format("{\"orderId\":%d,\"userId\":%d,\"voucherId\":%d,\"status\":%d}",
                order.getId(), order.getUserId(), order.getVoucherId(), order.getStatus());
    }
}
