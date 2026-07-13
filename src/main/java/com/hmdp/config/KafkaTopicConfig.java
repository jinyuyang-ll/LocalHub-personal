package com.hmdp.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(prefix = "localhub.kafka", name = "enabled", havingValue = "true")
public class KafkaTopicConfig {

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name("localhub.order.events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic seckillOrdersTopic() {
        return TopicBuilder.name("localhub.seckill.orders")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic seckillOrdersRetryTopic() {
        return TopicBuilder.name("localhub.seckill.orders.retry")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderEventsDltTopic() {
        return TopicBuilder.name("localhub.order.events.dlt")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
