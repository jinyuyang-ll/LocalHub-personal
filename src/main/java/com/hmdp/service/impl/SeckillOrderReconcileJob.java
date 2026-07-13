package com.hmdp.service.impl;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IOutboxEventService;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_STATUS_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_STATUS_TTL;

@Slf4j
@Component
public class SeckillOrderReconcileJob {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private IOutboxEventService outboxEventService;

    @Scheduled(fixedDelay = 120_000)
    public void reconcileProcessingOrders() {
        Set<String> keys = stringRedisTemplate.keys(SECKILL_ORDER_STATUS_KEY + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String key : keys) {
            String status = stringRedisTemplate.opsForValue().get(key);
            if (!"PROCESSING".equals(status)) {
                continue;
            }
            Long orderId = parseOrderId(key);
            if (orderId == null) {
                continue;
            }
            VoucherOrder order = voucherOrderService.getById(orderId);
            if (order == null) {
                stringRedisTemplate.opsForValue().set(key, "FAILED", SECKILL_ORDER_STATUS_TTL, TimeUnit.MINUTES);
                outboxEventService.createEvent(
                        "VoucherOrder",
                        orderId,
                        "SECKILL_ORDER_RECONCILE_FAILED",
                        "localhub.order.events",
                        "{\"orderId\":" + orderId + ",\"status\":\"FAILED\"}"
                );
                log.warn("Reconciled stuck seckill order as FAILED. orderId={}", orderId);
            }
        }
    }

    private Long parseOrderId(String key) {
        try {
            return Long.valueOf(key.substring(SECKILL_ORDER_STATUS_KEY.length()));
        } catch (Exception e) {
            return null;
        }
    }
}
