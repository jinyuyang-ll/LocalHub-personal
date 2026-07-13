package com.hmdp.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisBloomFilterTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RedisBloomFilter redisBloomFilter;

    @Test
    void shouldReturnFalseWhenAnyBitIsMissing() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getBit(eq("bloom:test"), anyLong())).thenReturn(true, true, false);

        boolean exists = redisBloomFilter.mightContain("bloom:test", 1L, 1000L);

        assertFalse(exists);
    }

    @Test
    void shouldReturnTrueWhenAllBitsExist() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getBit(eq("bloom:test"), anyLong())).thenReturn(true);

        boolean exists = redisBloomFilter.mightContain("bloom:test", 1L, 1000L);

        assertTrue(exists);
    }
}
