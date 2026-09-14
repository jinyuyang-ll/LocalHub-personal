package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.enums.SeckillOrderState;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IOutboxEventService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderCloseService {
    private static final int UNPAID = 1;
    private static final int CANCELLED = 4;

    @Resource private VoucherOrderMapper orderMapper;
    @Resource private IOutboxEventService outboxEventService;
    @Resource private OrderStatusService orderStatusService;

    @Transactional
    public int closeExpired(LocalDateTime expireTime) {
        List<VoucherOrder> orders = orderMapper.selectList(new QueryWrapper<VoucherOrder>()
                .eq("status", UNPAID).lt("create_time", expireTime).last("LIMIT 100"));
        int closed = 0;
        for (VoucherOrder order : orders) {
            int updated = orderMapper.update(null, new UpdateWrapper<VoucherOrder>()
                    .eq("id", order.getId()).eq("status", UNPAID).set("status", CANCELLED));
            if (updated == 1) {
                order.setStatus(CANCELLED);
                outboxEventService.createEvent("VoucherOrder", order.getId(), "VOUCHER_ORDER_CLOSED",
                        "localhub.order.events", OrderEventPayload.from(order));
                TransactionHooks.afterCommit(() -> orderStatusService.transition(order.getId(), SeckillOrderState.CLOSED));
                closed++;
            }
        }
        return closed;
    }
}
