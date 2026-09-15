package com.hmdp.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import javax.annotation.Resource;
import java.time.LocalDateTime;

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
            // No explicit lease: Redisson's watchdog renews the lock while this
            // instance is alive, preventing overlap during a slow database scan.
            acquired = lock.tryLock();
            if (!acquired) return;
            int closed = orderCloseService.closeExpired(LocalDateTime.now().minusMinutes(15));
            if (closed > 0) log.info("Closed {} expired unpaid orders", closed);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) lock.unlock();
        }
    }
}
