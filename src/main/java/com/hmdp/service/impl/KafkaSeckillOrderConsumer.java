package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.SeckillOrderMessage;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.config.LocalHubMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "localhub.seckill", name = "queue", havingValue = "kafka")
public class KafkaSeckillOrderConsumer {

    @Resource
    private VoucherOrderServiceImpl voucherOrderService;

    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;
    @Resource
    private LocalHubMetrics metrics;

    @Value("${localhub.kafka.topics.order-events-dlt:localhub.order.events.dlt}")
    private String dltTopic;

    @Value("${localhub.kafka.topics.seckill-orders-retry:localhub.seckill.orders.retry}")
    private String retryTopic;

    @Value("${localhub.seckill.retry.max-attempts:3}")
    private Integer maxAttempts;

    @KafkaListener(
            topics = "${localhub.kafka.topics.seckill-orders:localhub.seckill.orders}",
            groupId = "${spring.kafka.consumer.group-id:localhub-hmdp}",
            autoStartup = "${localhub.kafka.enabled:false}"
    )
    public void consume(String payload) {
        handle(payload);
    }

    @KafkaListener(
            topics = "${localhub.kafka.topics.seckill-orders-retry:localhub.seckill.orders.retry}",
            groupId = "${spring.kafka.consumer.group-id:localhub-hmdp}-retry",
            autoStartup = "${localhub.kafka.enabled:false}"
    )
    public void consumeRetry(String payload) {
        handle(payload);
    }

    private void handle(String payload) {
        SeckillOrderMessage message = JSONUtil.toBean(payload, SeckillOrderMessage.class);
        try {
            VoucherOrder voucherOrder = new VoucherOrder();
            voucherOrder.setId(message.getOrderId());
            voucherOrder.setUserId(message.getUserId());
            voucherOrder.setVoucherId(message.getVoucherId());
            voucherOrderService.createVoucherOrder(voucherOrder);
            metrics.increment("seckill.consumer", "success");
        } catch (Exception e) {
            metrics.increment("seckill.consumer", "failed");
            log.error("Kafka seckill order consume failed. payload={}", payload, e);
            publishFailure(message, e);
            throw new IllegalStateException(e);
        }
    }

    private void publishFailure(SeckillOrderMessage message, Exception e) {
        if (message == null) {
            kafkaTemplate.send(dltTopic, null, "");
            return;
        }
        int retryCount = message.getRetryCount() == null ? 1 : message.getRetryCount() + 1;
        message.setRetryCount(retryCount);
        message.setLastError(trimError(e.getMessage()));
        String payload = JSONUtil.toJsonStr(message);
        String key = String.valueOf(message.getOrderId());
        if (retryCount < maxAttempts) {
            metrics.increment("kafka.retry", "published");
            kafkaTemplate.send(retryTopic, key, payload);
        } else {
            metrics.increment("kafka.dlt", "published");
            kafkaTemplate.send(dltTopic, key, payload);
        }
    }

    private String trimError(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 256 ? error.substring(0, 256) : error;
    }
}
