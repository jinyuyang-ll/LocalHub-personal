package com.hmdp.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Component
@ConditionalOnProperty(prefix = "localhub.kafka", name = "enabled", havingValue = "true")
public class OrderEventConsumer {

    private static final String CONSUMER_NAME = "order-release";

    @Resource private OrderReleaseService orderReleaseService;
    @Resource private ConsumerMessageService consumerMessageService;

    @KafkaListener(topics = "${localhub.kafka.topics.order-events:localhub.order.events}",
            groupId = "${spring.kafka.consumer.group-id:localhub}-order-release",
            autoStartup = "${localhub.kafka.enabled:false}")
    @Transactional
    public void consume(String payload) {
        JSONObject envelope = JSONUtil.parseObj(payload);
        String eventId = envelope.getStr("eventId");
        String eventType = envelope.getStr("eventType");
        if (eventId == null || eventType == null) {
            throw new IllegalArgumentException("Order event envelope is missing eventId/eventType");
        }
        if (!consumerMessageService.tryRecord(CONSUMER_NAME, eventId, eventType)) return;

        JSONObject event = envelope.getJSONObject("data");
        if (event == null) return;
        Integer status = event.getInt("status");
        if (status == null || status != com.hmdp.enums.OrderStatus.CANCELLED.getCode()) return;
        orderReleaseService.release(event.getLong("orderId"), event.getLong("userId"),
                event.getLong("voucherId"), "ORDER_CANCELLED_OR_CLOSED");
    }
}
