package com.hmdp.service.impl;

import com.hmdp.config.LocalHubMetrics;
import com.hmdp.dto.SeckillOrderMessage;
import com.hmdp.enums.SeckillOrderState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Arrays;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Service
public class SeckillRollbackService {

    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT;

    static {
        ROLLBACK_SCRIPT = new DefaultRedisScript<>();
        ROLLBACK_SCRIPT.setLocation(new ClassPathResource("seckill_compensate.lua"));
        ROLLBACK_SCRIPT.setResultType(Long.class);
    }

    @Resource private StringRedisTemplate redis;
    @Resource private OrderStatusService orderStatusService;
    @Resource private LocalHubMetrics metrics;

    public boolean rollback(SeckillOrderMessage message, String reason) {
        return rollback(message, reason, SeckillOrderState.FAILED, false);
    }

    public boolean rollback(SeckillOrderMessage message, String reason, SeckillOrderState finalState,
                            boolean preserveUserMarker) {
        if (!valid(message)) return false;
        Long result = redis.execute(ROLLBACK_SCRIPT, Arrays.asList(
                        SECKILL_RESERVATION_KEY + message.getOrderId(),
                        SECKILL_ORDER_KEY + message.getVoucherId(),
                        SECKILL_STOCK_KEY + message.getVoucherId(),
                        SECKILL_ORDER_STATUS_KEY + message.getOrderId(),
                        SECKILL_RESERVATION_AUDIT_ZSET, SECKILL_RESERVATION_AUDIT_HASH),
                message.getVoucherId() + ":" + message.getUserId(),
                String.valueOf(message.getUserId()), String.valueOf(finalState.getTtlSeconds()), finalState.name(),
                preserveUserMarker ? "1" : "0", String.valueOf(message.getOrderId()));
        boolean restored = result != null && result == 1L;
        if (restored) orderStatusService.recordFailure(message.getOrderId(), reason);
        if (restored) metrics.incrementGauge("compensation.count");
        metrics.increment("seckill.rollback", restored ? "restored" : "idempotent_skip");
        log.warn("Seckill rollback. orderId={}, restored={}, state={}, reason={}",
                message.getOrderId(), restored, finalState, reason);
        return restored;
    }

    private boolean valid(SeckillOrderMessage message) {
        return message != null && message.getOrderId() != null
                && message.getUserId() != null && message.getVoucherId() != null;
    }
}
