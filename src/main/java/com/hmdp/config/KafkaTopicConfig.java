package com.hmdp.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.beans.factory.annotation.Value;

@Configuration
@ConditionalOnProperty(prefix = "localhub.kafka", name = "enabled", havingValue = "true")
public class KafkaTopicConfig {

    @Bean
    public NewTopic orderEventsTopic(@Value("${localhub.kafka.topics.order-events:localhub.order.events}") String name) {
        return TopicBuilder.name(name)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic seckillOrdersTopic(@Value("${localhub.kafka.topics.seckill-orders:localhub.seckill.orders}") String name) {
        return TopicBuilder.name(name)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic seckillOrdersRetryTopic(@Value("${localhub.kafka.topics.seckill-orders-retry:localhub.seckill.orders.retry}") String name) {
        return TopicBuilder.name(name)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderEventsDltTopic(@Value("${localhub.kafka.topics.order-events-dlt:localhub.order.events.dlt}") String name) {
        return TopicBuilder.name(name)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic seckillOrdersDltTopic(@Value("${localhub.kafka.topics.seckill-orders-dlt:localhub.seckill.orders.dlt}") String name) {
        return TopicBuilder.name(name)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
