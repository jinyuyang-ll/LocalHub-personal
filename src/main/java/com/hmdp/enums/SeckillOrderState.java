package com.hmdp.enums;

import static com.hmdp.utils.RedisConstants.SECKILL_FAILED_TTL_SECONDS;
import static com.hmdp.utils.RedisConstants.SECKILL_PENDING_TTL_SECONDS;
import static com.hmdp.utils.RedisConstants.SECKILL_SUCCESS_TTL_SECONDS;

public enum SeckillOrderState {
    PENDING(SECKILL_PENDING_TTL_SECONDS),
    PROCESSING(SECKILL_PENDING_TTL_SECONDS),
    SUCCESS(SECKILL_SUCCESS_TTL_SECONDS),
    FAILED(SECKILL_FAILED_TTL_SECONDS),
    DUPLICATE(SECKILL_FAILED_TTL_SECONDS),
    CLOSED(SECKILL_SUCCESS_TTL_SECONDS);

    private final long ttlSeconds;

    SeckillOrderState(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }
}
