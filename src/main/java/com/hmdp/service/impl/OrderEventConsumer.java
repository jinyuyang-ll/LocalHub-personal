package com.hmdp.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@ConditionalOnProperty(prefix = "localhub.kafka", name = "enabled", havingValue = "true")
public class OrderEventConsumer {

    @Resource private OrderReleaseService orderReleaseService;

    @KafkaListener(topics = "${localhub.kafka.topics.order-events:localhub.order.events}",
            groupId = "${spring.kafka.consumer.group-id:localhub-hmdp}-order-release",
            autoStartup = "${localhub.kafka.enabled:false}")
    public void consume(String payload) {
        JSONObject event = JSONUtil.parseObj(payload);
        Integer status = event.getInt("status");
        if (status == null || status != com.hmdp.enums.OrderStatus.CANCELLED.getCode()) return;
        orderReleaseService.release(event.getLong("orderId"), event.getLong("userId"),
                event.getLong("voucherId"), "ORDER_CANCELLED_OR_CLOSED");
    }
}
