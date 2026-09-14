package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.entity.VoucherOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "localhub.seckill", name = "queue", havingValue = "redis-stream", matchIfMissing = true)
public class SeckillMessageConsumer {
    @Resource private StringRedisTemplate redis;
    @Resource private OrderCreateService orderCreateService;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "seckill-stream-consumer"));
    private volatile boolean running = true;

    @PostConstruct
    public void start() {
        createGroupIfMissing();
        executor.submit(this::consumeLoop);
    }

    @PreDestroy
    public void stop() {
        running = false;
        executor.shutdownNow();
    }

    private void createGroupIfMissing() {
        try {
            redis.execute((RedisCallback<Object>) connection -> connection.execute("XGROUP",
                    "CREATE".getBytes(), STREAM_ORDERS_KEY.getBytes(), STREAM_ORDERS_GROUP.getBytes(),
                    "0".getBytes(), "MKSTREAM".getBytes()));
        } catch (RuntimeException error) {
            if (error.getMessage() == null || !error.getMessage().contains("BUSYGROUP")) throw error;
        }
    }

    private void consumeLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                List<MapRecord<String, Object, Object>> records = read(ReadOffset.lastConsumed(), true);
                if (records == null || records.isEmpty()) continue;
                process(records.get(0));
            } catch (Exception error) {
                if (!running || Thread.currentThread().isInterrupted()) break;
                log.error("Redis Stream seckill consume failed", error);
                drainPending();
            }
        }
    }

    private void drainPending() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                List<MapRecord<String, Object, Object>> records = read(ReadOffset.from("0"), false);
                if (records == null || records.isEmpty()) return;
                process(records.get(0));
            } catch (Exception error) {
                log.error("Redis Stream pending message failed", error);
                return;
            }
        }
    }

    private List<MapRecord<String, Object, Object>> read(ReadOffset offset, boolean block) {
        StreamReadOptions options = StreamReadOptions.empty().count(1);
        if (block) options = options.block(Duration.ofSeconds(2));
        return redis.opsForStream().read(Consumer.from(STREAM_ORDERS_GROUP, STREAM_ORDERS_CONSUMER), options,
                StreamOffset.create(STREAM_ORDERS_KEY, offset));
    }

    private void process(MapRecord<String, Object, Object> record) {
        Map<Object, Object> values = record.getValue();
        VoucherOrder order = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
        orderCreateService.createOrder(order);
        redis.opsForStream().acknowledge(STREAM_ORDERS_KEY, STREAM_ORDERS_GROUP, record.getId());
    }
}
