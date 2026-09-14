package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.enums.SeckillOrderState;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_FAILURE_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_OWNER_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_STATUS_KEY;

@Service
public class OrderStatusService {

    @Resource private VoucherOrderMapper orderMapper;
    @Resource private StringRedisTemplate redis;

    public void transition(Long orderId, SeckillOrderState state) {
        redis.opsForValue().set(SECKILL_ORDER_STATUS_KEY + orderId, state.name(),
                state.getTtlSeconds(), TimeUnit.SECONDS);
    }

    public void recordFailure(Long orderId, String reason) {
        String safeReason = reason == null ? "unknown" : reason;
        if (safeReason.length() > 512) safeReason = safeReason.substring(0, 512);
        redis.opsForValue().set(SECKILL_ORDER_FAILURE_KEY + orderId, safeReason,
                SeckillOrderState.FAILED.getTtlSeconds(), TimeUnit.SECONDS);
    }

    public Result queryStatus(Long orderId) {
        VoucherOrder order = orderMapper.selectById(orderId);
        if (order != null) {
            if (!order.getUserId().equals(currentUserId())) return Result.fail("无权查看该订单");
            return Result.ok(order.getStatus());
        }
        String owner = redis.opsForValue().get(SECKILL_ORDER_OWNER_KEY + orderId);
        if (owner == null) return Result.fail("订单不存在");
        if (!owner.equals(String.valueOf(currentUserId()))) return Result.fail("无权查看该订单");
        String status = redis.opsForValue().get(SECKILL_ORDER_STATUS_KEY + orderId);
        if (status == null) return Result.fail("订单不存在");
        return Result.ok(status);
    }

    public Result queryOrder(Long orderId) {
        VoucherOrder order = orderMapper.selectById(orderId);
        if (order == null) return Result.fail("订单不存在或正在处理中");
        if (!order.getUserId().equals(currentUserId())) return Result.fail("无权查看该订单");
        return Result.ok(order);
    }

    private Long currentUserId() {
        return UserHolder.getUser() == null ? -1L : UserHolder.getUser().getId();
    }
}
