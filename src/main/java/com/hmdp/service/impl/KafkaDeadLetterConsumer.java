package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.SeckillOrderMessage;
import com.hmdp.service.IOutboxEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_STATUS_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_STATUS_TTL;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "localhub.kafka", name = "enabled", havingValue = "true")
public class KafkaDeadLetterConsumer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IOutboxEventService outboxEventService;

    @KafkaListener(
            topics = "${localhub.kafka.topics.order-events-dlt:localhub.order.events.dlt}",
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
            stringRedisTemplate.opsForValue().set(
                    SECKILL_ORDER_STATUS_KEY + message.getOrderId(),
                    "FAILED",
                    SECKILL_ORDER_STATUS_TTL,
                    TimeUnit.MINUTES
            );
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
