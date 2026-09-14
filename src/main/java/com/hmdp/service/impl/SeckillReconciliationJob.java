package com.hmdp.service.impl;

import com.hmdp.config.LocalHubMetrics;
import com.hmdp.dto.SeckillOrderMessage;
import com.hmdp.mapper.VoucherOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class SeckillReconciliationJob {

    @Resource private RedissonClient redissonClient;
    @Resource private StringRedisTemplate redis;
    @Resource private VoucherOrderMapper orderMapper;
    @Resource private ObjectProvider<SeckillKafkaRecoveryService> recoveryProvider;
    @Resource private OrderReleaseService orderReleaseService;
    @Resource private LocalHubMetrics metrics;

    @Value("${localhub.seckill.reconciliation.stale-seconds:300}") private long staleSeconds;

    @Scheduled(fixedDelayString = "${localhub.seckill.reconciliation.interval-ms:60000}")
    public void reconcile() {
        RLock lock = redissonClient.getLock(SECKILL_RECONCILE_JOB_LOCK_KEY);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, 55, TimeUnit.SECONDS);
            if (!acquired) return;
            reconcileReservations();
            orderReleaseService.retryPendingRedisReleases(100);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException error) {
            log.error("Seckill reconciliation failed", error);
            metrics.increment("seckill.reconciliation", "failed");
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    private void reconcileReservations() {
        long nowSeconds = System.currentTimeMillis() / 1000;
        Set<String> stale = redis.opsForZSet().rangeByScore(SECKILL_RESERVATION_AUDIT_ZSET,
                0, nowSeconds - staleSeconds, 0, 100);
        Set<String> oldest = redis.opsForZSet().range(SECKILL_RESERVATION_AUDIT_ZSET, 0, 0);
        long age = 0;
        if (oldest != null && !oldest.isEmpty()) {
            Double score = redis.opsForZSet().score(SECKILL_RESERVATION_AUDIT_ZSET, oldest.iterator().next());
            if (score != null) age = Math.max(0, nowSeconds - score.longValue());
        }
        metrics.setGauge("seckill.processing.age.seconds", age);
        if (stale == null) return;
        for (String orderIdText : stale) reconcileOne(orderIdText);
    }

    private void reconcileOne(String orderIdText) {
        Long orderId;
        try {
            orderId = Long.valueOf(orderIdText);
        } catch (NumberFormatException invalid) {
            redis.opsForZSet().remove(SECKILL_RESERVATION_AUDIT_ZSET, orderIdText);
            return;
        }
        if (orderMapper.selectById(orderId) != null) {
            SeckillKafkaRecoveryService recovery = recoveryProvider.getIfAvailable();
            if (recovery != null) recovery.complete(orderId);
            else {
                redis.delete(SECKILL_RESERVATION_KEY + orderId);
                redis.opsForZSet().remove(SECKILL_RESERVATION_AUDIT_ZSET, orderIdText);
            }
            metrics.increment("seckill.reconciliation", "order_exists_cleanup");
            return;
        }
        String reservation = redis.opsForValue().get(SECKILL_RESERVATION_KEY + orderId);
        if (reservation == null) {
            Object durable = redis.opsForHash().get(SECKILL_RESERVATION_AUDIT_HASH, orderIdText);
            reservation = durable == null ? null : String.valueOf(durable);
        }
        if (reservation == null) {
            redis.opsForZSet().remove(SECKILL_RESERVATION_AUDIT_ZSET, orderIdText);
            metrics.increment("seckill.reconciliation", "missing_audit_payload");
            return;
        }
        String[] parts = reservation.split(":", 2);
        if (parts.length != 2) return;
        SeckillOrderMessage message = new SeckillOrderMessage();
        message.setOrderId(orderId);
        message.setVoucherId(Long.valueOf(parts[0]));
        message.setUserId(Long.valueOf(parts[1]));
        SeckillKafkaRecoveryService recovery = recoveryProvider.getIfAvailable();
        if (recovery != null) {
            recovery.schedulePublish(message, new IllegalStateException("reconciliation found stale reservation"));
            redis.opsForZSet().add(SECKILL_RESERVATION_AUDIT_ZSET, orderIdText, System.currentTimeMillis() / 1000);
            metrics.increment("seckill.reconciliation", "republished");
        } else {
            metrics.increment("seckill.reconciliation", "stream_pending");
        }
    }
}
