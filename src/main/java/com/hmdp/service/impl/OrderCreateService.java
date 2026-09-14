package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hmdp.dto.SeckillOrderMessage;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.enums.SeckillOrderState;
import com.hmdp.exception.BusinessException;
import com.hmdp.exception.ErrorCode;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IOutboxEventService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.ORDER_LOCK_KEY;

@Service
public class OrderCreateService {

    @Resource private VoucherOrderMapper orderMapper;
    @Resource private SeckillVoucherMapper seckillVoucherMapper;
    @Resource private RedissonClient redissonClient;
    @Resource private IOutboxEventService outboxEventService;
    @Resource private OrderStatusService orderStatusService;
    @Resource private SeckillCompensationService compensationService;
    @Resource private ObjectProvider<SeckillKafkaRecoveryService> recoveryProvider;

    @Transactional
    public void createOrder(VoucherOrder order) {
        if (orderMapper.selectById(order.getId()) != null) {
            afterCommit(order.getId());
            return;
        }
        RLock lock = redissonClient.getLock(ORDER_LOCK_KEY + order.getUserId());
        if (!lock.tryLock()) throw new BusinessException(ErrorCode.RETRYABLE_ERROR, "订单创建锁繁忙");
        try {
            if (orderMapper.selectById(order.getId()) != null) {
                afterCommit(order.getId());
                return;
            }
            Integer count = orderMapper.selectCount(new QueryWrapper<VoucherOrder>()
                    .eq("user_id", order.getUserId()).eq("voucher_id", order.getVoucherId()));
            if (count != null && count > 0) {
                compensationService.compensateDuplicate(message(order), "database duplicate order");
                return;
            }
            int stockUpdated = seckillVoucherMapper.update(null, new UpdateWrapper<SeckillVoucher>()
                    .setSql("stock = stock - 1").eq("voucher_id", order.getVoucherId()).gt("stock", 0));
            if (stockUpdated == 0) {
                compensationService.compensateFailure(message(order), "database stock exhausted");
                return;
            }
            if (orderMapper.insert(order) != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "订单写入失败");
            }
            outboxEventService.createEvent("VoucherOrder", order.getId(), "VOUCHER_ORDER_CREATED",
                    "localhub.order.events", OrderEventPayload.from(order));
            afterCommit(order.getId());
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    private void afterCommit(Long orderId) {
        Runnable action = () -> {
            orderStatusService.transition(orderId, SeckillOrderState.SUCCESS);
            SeckillKafkaRecoveryService recovery = recoveryProvider.getIfAvailable();
            if (recovery != null) recovery.complete(orderId);
        };
        TransactionHooks.afterCommit(action);
    }

    private SeckillOrderMessage message(VoucherOrder order) {
        SeckillOrderMessage message = new SeckillOrderMessage();
        message.setOrderId(order.getId());
        message.setUserId(order.getUserId());
        message.setVoucherId(order.getVoucherId());
        return message;
    }
}
