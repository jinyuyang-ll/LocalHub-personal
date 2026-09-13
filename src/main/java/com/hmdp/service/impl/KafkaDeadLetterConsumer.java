package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.SeckillOrderMessage;
import com.hmdp.service.IOutboxEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "localhub.kafka", name = "enabled", havingValue = "true")
public class KafkaDeadLetterConsumer {

    @Resource
    private SeckillKafkaRecoveryService recoveryService;

    @Resource
    private IOutboxEventService outboxEventService;

    @KafkaListener(
            topics = "${localhub.kafka.topics.seckill-orders-dlt:localhub.seckill.orders.dlt}",
            groupId = "${spring.kafka.consumer.group-id:localhub-hmdp}-dlt",
            autoStartup = "${localhub.kafka.enabled:false}"
    )
    public void consume(String payload) {
        SeckillOrderMessage message = null;
        try {
            message = JSONUtil.toBean(payload, SeckillOrderMessage.class);
        } catch (Exception e) {
            log.warn("Dead letter payload is not a seckill order message. payload={}", payload);
        }

        if (message != null && message.getOrderId() != null) {
            recoveryService.compensate(message, "dead letter consumed");
            outboxEventService.createEvent(
                    "VoucherOrder",
                    message.getOrderId(),
                    "SECKILL_ORDER_DEAD_LETTER",
                    "localhub.order.events",
                    payload
            );
        }
        log.error("Kafka dead letter consumed. payload={}", payload);
    }
}
