package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.enums.SeckillOrderState;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IOutboxEventService;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Service
public class OrderCancelService {
    private static final int UNPAID = 1;
    private static final int CANCELLED = 4;

    @Resource private VoucherOrderMapper orderMapper;
    @Resource private IOutboxEventService outboxEventService;
    @Resource private OrderStatusService orderStatusService;

    @Transactional
    public Result cancel(Long orderId) {
        VoucherOrder order = orderMapper.selectById(orderId);
        if (order == null) return Result.fail("订单不存在");
        if (!order.getUserId().equals(UserHolder.getUser().getId())) return Result.fail("无权操作该订单");
        if (order.getStatus() == null || order.getStatus() != UNPAID) return Result.fail("订单当前状态不可取消");
        int updated = orderMapper.update(null, new UpdateWrapper<VoucherOrder>()
                .eq("id", orderId).eq("user_id", order.getUserId()).eq("status", UNPAID)
                .set("status", CANCELLED));
        if (updated != 1) return Result.fail("订单取消失败，请刷新后重试");
        order.setStatus(CANCELLED);
        outboxEventService.createEvent("VoucherOrder", orderId, "VOUCHER_ORDER_CANCELLED",
                "localhub.order.events", OrderEventPayload.from(order));
        TransactionHooks.afterCommit(() -> orderStatusService.transition(orderId, SeckillOrderState.CLOSED));
        return Result.ok();
    }
}
