package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.config.LocalHubMetrics;
import com.hmdp.dto.SeckillOrderMessage;
import com.hmdp.enums.SeckillOrderState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "localhub.seckill", name = "queue", havingValue = "kafka")
public class SeckillKafkaRecoveryService {

    @Resource private StringRedisTemplate redis;
    @Resource private KafkaTemplate<String, String> kafkaTemplate;
    @Resource private LocalHubMetrics metrics;
    @Resource private SeckillCompensationService compensationService;
    @Resource private OrderStatusService orderStatusService;

    @Value("${localhub.kafka.topics.seckill-orders:localhub.seckill.orders}")
    private String ordersTopic;

    @Value("${localhub.kafka.topics.seckill-orders-dlt:localhub.seckill.orders.dlt}")
    private String dltTopic;

    @Value("${localhub.seckill.publish-retry.max-attempts:5}")
    private int maxPublishAttempts;

    public void schedulePublish(SeckillOrderMessage message, Throwable error) {
        int attempts = message.getRetryCount() == null ? 1 : message.getRetryCount() + 1;
        message.setRetryCount(attempts);
        message.setLastError(trim(error));
        String orderId = String.valueOf(message.getOrderId());
        redis.opsForHash().put(SECKILL_PUBLISH_PAYLOAD_HASH, orderId, JSONUtil.toJsonStr(message));
        redis.opsForZSet().add(SECKILL_PUBLISH_RETRY_ZSET, orderId,
                System.currentTimeMillis() + retryDelayMillis(attempts));
        metrics.increment("seckill.publish", "scheduled_retry");
    }

    public void complete(Long orderId) {
        String id = String.valueOf(orderId);
        redis.delete(SECKILL_RESERVATION_KEY + id);
        redis.opsForHash().delete(SECKILL_PUBLISH_PAYLOAD_HASH, id);
        redis.opsForZSet().remove(SECKILL_PUBLISH_RETRY_ZSET, id);
    }

    public boolean compensate(SeckillOrderMessage message, String reason) {
        boolean restored = compensationService.compensateFailure(message, reason);
        if (message != null && message.getOrderId() != null) complete(message.getOrderId());
        return restored;
    }

    @Scheduled(fixedDelayString = "${localhub.seckill.publish-retry.interval-ms:5000}")
    public void retryPendingPublishes() {
        Set<String> due = redis.opsForZSet().rangeByScore(SECKILL_PUBLISH_RETRY_ZSET, 0,
                System.currentTimeMillis(), 0, 50);
        if (due == null) return;
        for (String orderId : due) retryOne(orderId);
    }

    private void retryOne(String orderId) {
        Object raw = redis.opsForHash().get(SECKILL_PUBLISH_PAYLOAD_HASH, orderId);
        if (raw == null) {
            redis.opsForZSet().remove(SECKILL_PUBLISH_RETRY_ZSET, orderId);
            return;
        }
        SeckillOrderMessage message = JSONUtil.toBean(String.valueOf(raw), SeckillOrderMessage.class);
        try {
            kafkaTemplate.send(ordersTopic, orderId, JSONUtil.toJsonStr(message)).get(10, TimeUnit.SECONDS);
            redis.opsForHash().delete(SECKILL_PUBLISH_PAYLOAD_HASH, orderId);
            redis.opsForZSet().remove(SECKILL_PUBLISH_RETRY_ZSET, orderId);
            orderStatusService.transition(message.getOrderId(), SeckillOrderState.PROCESSING);
            metrics.increment("seckill.publish", "retry_success");
        } catch (Exception e) {
            int attempts = message.getRetryCount() == null ? 1 : message.getRetryCount() + 1;
            if (attempts >= maxPublishAttempts) {
                publishDltAndCompensate(message, e);
            } else {
                schedulePublish(message, e);
            }
        }
    }

    private void publishDltAndCompensate(SeckillOrderMessage message, Exception error) {
        try {
            message.setLastError(trim(error));
            kafkaTemplate.send(dltTopic, String.valueOf(message.getOrderId()), JSONUtil.toJsonStr(message))
                    .get(10, TimeUnit.SECONDS);
            compensate(message, "publish retries exhausted");
            metrics.increment("kafka.dlt", "published");
        } catch (Exception dltError) {
            schedulePublish(message, dltError);
            log.error("Failed to publish seckill DLT. orderId={}", message.getOrderId(), dltError);
        }
    }

    private long retryDelayMillis(int attempts) {
        return Math.min(60_000L, 1_000L << Math.min(attempts, 6));
    }

    private String trim(Throwable error) {
        if (error == null || error.getMessage() == null) return null;
        String message = error.getMessage();
        return message.length() > 256 ? message.substring(0, 256) : message;
    }
}
