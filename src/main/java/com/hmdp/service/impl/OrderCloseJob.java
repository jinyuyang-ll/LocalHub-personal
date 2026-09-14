package com.hmdp.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.ORDER_CLOSE_JOB_LOCK_KEY;

@Slf4j
@Component
public class OrderCloseJob {
    @Resource private OrderCloseService orderCloseService;
    @Resource private RedissonClient redissonClient;

    @Scheduled(fixedDelayString = "${localhub.order.close-interval-ms:60000}")
    public void closeExpiredOrders() {
        RLock lock = redissonClient.getLock(ORDER_CLOSE_JOB_LOCK_KEY);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, 55, TimeUnit.SECONDS);
            if (!acquired) return;
            int closed = orderCloseService.closeExpired(LocalDateTime.now().minusMinutes(15));
            if (closed > 0) log.info("Closed {} expired unpaid orders", closed);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) lock.unlock();
        }
    }
}
