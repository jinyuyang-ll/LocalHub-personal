package com.hmdp.service.impl;

import com.hmdp.config.LocalHubMetrics;
import com.hmdp.enums.SeckillOrderState;
import com.hmdp.exception.BusinessException;
import com.hmdp.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Service
public class OrderReleaseService {
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>();

    static {
        RELEASE_SCRIPT.setLocation(new ClassPathResource("seckill_order_release.lua"));
        RELEASE_SCRIPT.setResultType(Long.class);
    }

    @Resource private JdbcTemplate jdbc;
    @Resource private StringRedisTemplate redis;
    @Resource private LocalHubMetrics metrics;

    @Transactional
    public boolean release(Long orderId, Long userId, Long voucherId, String reason) {
        int inserted = jdbc.update("INSERT IGNORE INTO tb_order_release(order_id,user_id,voucher_id,reason) VALUES (?,?,?,?)",
                orderId, userId, voucherId, reason);
        if (inserted == 1) {
            int updated = jdbc.update("UPDATE tb_seckill_voucher SET stock = stock + 1 WHERE voucher_id = ?", voucherId);
            if (updated != 1) throw new BusinessException(ErrorCode.INTERNAL_ERROR, "取消订单库存恢复失败");
        }
        TransactionHooks.afterCommit(() -> syncRedis(orderId, userId, voucherId));
        return inserted == 1;
    }

    public void retryPendingRedisReleases(int limit) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT order_id,user_id,voucher_id FROM tb_order_release WHERE redis_released = 0 ORDER BY create_time,id LIMIT ?",
                limit);
        for (Map<String, Object> row : rows) {
            syncRedis(((Number) row.get("order_id")).longValue(), ((Number) row.get("user_id")).longValue(),
                    ((Number) row.get("voucher_id")).longValue());
        }
    }

    private void syncRedis(Long orderId, Long userId, Long voucherId) {
        try {
            Long released = redis.execute(RELEASE_SCRIPT, Arrays.asList(SECKILL_ORDER_KEY + voucherId, SECKILL_STOCK_KEY + voucherId,
                            SECKILL_ORDER_STATUS_KEY + orderId, SECKILL_RESERVATION_KEY + orderId,
                            SECKILL_RESERVATION_AUDIT_ZSET, SECKILL_RESERVATION_AUDIT_HASH), String.valueOf(userId),
                    String.valueOf(SeckillOrderState.CLOSED.getTtlSeconds()), String.valueOf(orderId));
            if (released == null || released != 1L) throw new IllegalStateException("Redis release script returned no acknowledgement");
            jdbc.update("UPDATE tb_order_release SET redis_released = 1, released_time = NOW() WHERE order_id = ?", orderId);
            metrics.increment("order.release", "success");
        } catch (RuntimeException error) {
            metrics.increment("order.release", "retry_pending");
            log.error("Redis order release will be reconciled. orderId={}", orderId, error);
        }
    }
}
