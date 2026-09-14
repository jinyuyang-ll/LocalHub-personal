package com.hmdp.utils;

import com.hmdp.entity.Shop;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class CacheClientTest {
    @Test void cacheHitDoesNotQueryDatabase() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("cache:shop:1")).thenReturn("{\"id\":1,\"name\":\"cached\"}");
        Function<Long, Shop> fallback = mock(Function.class);
        Shop shop = new CacheClient(redis).queryWithPassThrough("cache:shop:", 1L, Shop.class,
                fallback, 30L, TimeUnit.MINUTES);
        assertEquals("cached", shop.getName());
        verifyNoInteractions(fallback);
    }

    @Test void nullDatabaseValueIsCachedToPreventPenetration() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("cache:shop:2")).thenReturn(null);
        Shop result = new CacheClient(redis).queryWithPassThrough("cache:shop:", 2L, Shop.class,
                ignored -> null, 30L, TimeUnit.MINUTES);
        assertNull(result);
        verify(values).set("cache:shop:2", "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
    }
}
