package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.enums.OrderStatus;
import com.hmdp.service.IOutboxEventService;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class OrderPaymentService {
    @Resource private VoucherOrderMapper orderMapper;
    @Resource private IOutboxEventService outboxEventService;

    @Transactional
    public Result pay(Long orderId) {
        VoucherOrder order = orderMapper.selectById(orderId);
        if (order == null) return Result.fail("订单不存在");
        if (!order.getUserId().equals(UserHolder.getUser().getId())) return Result.fail("无权操作该订单");
        OrderStatus current = OrderStatus.fromCode(order.getStatus());
        if (current == null || !current.canTransitionTo(OrderStatus.PAID)) return Result.fail("订单当前状态不可支付");
        LocalDateTime paidAt = LocalDateTime.now();
        int updated = orderMapper.update(null, new UpdateWrapper<VoucherOrder>()
                .eq("id", orderId).eq("user_id", order.getUserId()).eq("status", OrderStatus.UNPAID.getCode())
                .set("status", OrderStatus.PAID.getCode()).set("pay_time", paidAt));
        if (updated != 1) return Result.fail("订单支付失败，请刷新后重试");
        order.setStatus(OrderStatus.PAID.getCode()); order.setPayTime(paidAt);
        outboxEventService.createEvent("VoucherOrder", orderId, "VOUCHER_ORDER_PAID",
                "localhub.order.events", OrderEventPayload.from(order));
        return Result.ok();
    }
}
