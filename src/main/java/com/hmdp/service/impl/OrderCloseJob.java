package com.hmdp.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Slf4j
@Component
public class OrderCloseJob {
    @Resource private OrderCloseService orderCloseService;

    @Scheduled(fixedDelayString = "${localhub.order.close-interval-ms:60000}")
    public void closeExpiredOrders() {
        int closed = orderCloseService.closeExpired(LocalDateTime.now().minusMinutes(15));
        if (closed > 0) log.info("Closed {} expired unpaid orders", closed);
    }
}
