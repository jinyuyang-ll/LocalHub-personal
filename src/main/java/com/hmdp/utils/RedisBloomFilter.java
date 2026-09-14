package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collection;
import java.util.UUID;

@Component
public class RedisBloomFilter {

    private static final int[] SEEDS = {17, 31, 61, 127};

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void put(String key, Long value, long size) {
        for (int seed : SEEDS) {
            stringRedisTemplate.opsForValue().setBit(key, hash(value, seed, size), true);
        }
    }

    public boolean mightContain(String key, Long value, long size) {
        for (int seed : SEEDS) {
            Boolean bit = stringRedisTemplate.opsForValue().getBit(key, hash(value, seed, size));
            if (bit == null || !bit) {
                return false;
            }
        }
        return true;
    }

    public void rebuild(String key, Collection<Long> values, long size) {
        String temporaryKey = key + ":rebuild:" + UUID.randomUUID();
        stringRedisTemplate.delete(temporaryKey);
        if (values == null || values.isEmpty()) {
            stringRedisTemplate.opsForValue().setBit(temporaryKey, 0, false);
        } else {
            for (Long value : values) put(temporaryKey, value, size);
        }
        stringRedisTemplate.rename(temporaryKey, key);
    }

    private long hash(Long value, int seed, long size) {
        long hash = value == null ? 0 : value;
        hash ^= (hash >>> 33);
        hash *= seed;
        hash ^= (hash >>> 29);
        return Math.floorMod(hash, size);
    }
}
