package com.hmdp.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_CODE_LIMIT_KEY = "login:code:limit:";
    public static final Long LOGIN_CODE_LIMIT_TTL = 60L;
    public static final String LOGIN_FAIL_KEY = "login:fail:";
    public static final Long LOGIN_FAIL_TTL = 10L;
    public static final int LOGIN_FAIL_MAX_TIMES = 5;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final String CACHE_SHOP_SEARCH_KEY = "cache:shop:search:";
    public static final String CACHE_SHOP_SEARCH_VERSION_KEY = "cache:shop:search:version";
    public static final String SHOP_BLOOM_KEY = "bloom:shop";
    public static final Long SHOP_BLOOM_SIZE = 1_000_000L;

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String SECKILL_ORDER_KEY = "seckill:order:";
    public static final String SECKILL_ORDER_STATUS_KEY = "seckill:order:status:";
    public static final String SECKILL_ORDER_OWNER_KEY = "seckill:order:owner:";
    public static final String SECKILL_ORDER_FAILURE_KEY = "seckill:order:failure:";
    public static final String SECKILL_RESERVATION_KEY = "seckill:reservation:";
    public static final String SECKILL_PUBLISH_PAYLOAD_HASH = "seckill:kafka:publish:payload";
    public static final String SECKILL_PUBLISH_RETRY_ZSET = "seckill:kafka:publish:retry";
    public static final String ORDER_LOCK_KEY = "lock:order:";
    public static final String STREAM_ORDERS_KEY = "stream.orders";
    public static final String STREAM_ORDERS_GROUP = "g1";
    public static final String STREAM_ORDERS_CONSUMER = "c1";
    public static final long SECKILL_PENDING_TTL_SECONDS = 30 * 60L;
    public static final long SECKILL_SUCCESS_TTL_SECONDS = 24 * 60 * 60L;
    public static final long SECKILL_FAILED_TTL_SECONDS = 7 * 24 * 60 * 60L;

    public static final String AI_CONVERSATION_KEY = "ai:conversation:";
    public static final String AI_RESERVATION_CONFIRM_KEY = "ai:reservation:confirm:";
    public static final String AI_RESERVATION_RESULT_KEY = "ai:reservation:result:";
    public static final String AI_RESERVATION_LOCK_KEY = "ai:reservation:lock:";
    public static final String AI_RESERVATION_STATE_KEY = "ai:reservation:state:";
    public static final String RATE_LIMIT_KEY = "rate:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
}
