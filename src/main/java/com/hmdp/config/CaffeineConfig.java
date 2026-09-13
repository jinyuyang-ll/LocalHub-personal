package com.hmdp.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hmdp.entity.Shop;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;
import java.util.List;

@Configuration
public class CaffeineConfig {

    @Bean
    public Cache<Long, Shop> shopLocalCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
    }

    @Bean("shopSearchLocalCache")
    public Cache<String, List<Shop>> shopSearchLocalCache() {
        return Caffeine.newBuilder()
                .maximumSize(2_000)
                .expireAfterWrite(2, TimeUnit.MINUTES)
                .build();
    }
}
