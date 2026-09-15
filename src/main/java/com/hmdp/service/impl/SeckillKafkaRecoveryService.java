package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.config.LocalHubMetrics;
import com.hmdp.dto.SeckillOrderMessage;
import com.hmdp.enums.SeckillOrderState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "localhub.seckill", name = "queue", havingValue = "kafka")
public class SeckillKafkaRecoveryService {

    private static final DefaultRedisScript<Long> SCHEDULE_SCRIPT = longScript("seckill_retry_schedule.lua");
    private static final DefaultRedisScript<List> CLAIM_SCRIPT = listScript("seckill_retry_claim.lua");
    private static final DefaultRedisScript<Long> ACK_SCRIPT = longScript("seckill_retry_ack.lua");
    private static final DefaultRedisScript<Long> REQUEUE_SCRIPT = longScript("seckill_retry_requeue.lua");
    private static final DefaultRedisScript<Long> COMPLETE_SCRIPT = longScript("seckill_retry_complete.lua");
    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();
    private final String workerId = UUID.randomUUID().toString();
    private final ThreadPoolExecutor publishExecutor = new ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(10), runnable -> {
                Thread thread = new Thread(runnable, "seckill-recovery-" + THREAD_SEQUENCE.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

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

    @Value("${localhub.seckill.publish-retry.lease-ms:60000}")
    private long leaseMillis;

    @Value("${localhub.seckill.publish-retry.batch-size:10}")
    private int batchSize;

    public void schedulePublish(SeckillOrderMessage message, Throwable error) {
        int attempts = message.getRetryCount() == null ? 1 : message.getRetryCount() + 1;
        message.setRetryCount(attempts);
        message.setLastError(trim(error));
        String orderId = String.valueOf(message.getOrderId());
        Long scheduled = redis.execute(SCHEDULE_SCRIPT, java.util.Arrays.asList(SECKILL_PUBLISH_PAYLOAD_HASH,
                        SECKILL_PUBLISH_RETRY_ZSET, SECKILL_PUBLISH_PROCESSING_ZSET,
                        SECKILL_PUBLISH_CLAIM_HASH),
                orderId, JSONUtil.toJsonStr(message),
                String.valueOf(System.currentTimeMillis() + retryDelayMillis(attempts)));
        metrics.increment("seckill.publish", scheduled != null && scheduled == 1L
                ? "scheduled_retry" : "active_claim_preserved");
    }

    public void complete(Long orderId) {
        String id = String.valueOf(orderId);
        redis.execute(COMPLETE_SCRIPT, java.util.Arrays.asList(SECKILL_RESERVATION_KEY + id,
                        SECKILL_PUBLISH_PAYLOAD_HASH, SECKILL_PUBLISH_RETRY_ZSET,
                        SECKILL_PUBLISH_PROCESSING_ZSET, SECKILL_PUBLISH_CLAIM_HASH,
                        SECKILL_RESERVATION_AUDIT_ZSET,
                        SECKILL_RESERVATION_AUDIT_HASH), id);
    }

    public boolean compensate(SeckillOrderMessage message, String reason) {
        boolean restored = compensationService.compensateFailure(message, reason);
        if (message != null && message.getOrderId() != null) complete(message.getOrderId());
        return restored;
    }

    @Scheduled(fixedDelayString = "${localhub.seckill.publish-retry.interval-ms:5000}")
    public void retryPendingPublishes() {
        // Do not accumulate leased work in a local queue. One batch always finishes
        // before this worker claims another, so the lease budget is predictable.
        if (publishExecutor.getActiveCount() > 0 || !publishExecutor.getQueue().isEmpty()) return;
        long now = System.currentTimeMillis();
        int limit = Math.max(1, Math.min(batchSize, 20));
        String tokenPrefix = workerId + ":" + UUID.randomUUID();
        List<?> claimed = redis.execute(CLAIM_SCRIPT,
                java.util.Arrays.asList(SECKILL_PUBLISH_RETRY_ZSET, SECKILL_PUBLISH_PROCESSING_ZSET,
                        SECKILL_PUBLISH_CLAIM_HASH),
                String.valueOf(now), String.valueOf(now + leaseMillis), String.valueOf(limit), tokenPrefix);
        metrics.setGauge("kafka.retry.count", retryBacklog());
        if (claimed == null) return;
        for (int i = 0; i + 1 < claimed.size(); i += 2) {
            String orderId = String.valueOf(claimed.get(i));
            String claimToken = String.valueOf(claimed.get(i + 1));
            publishExecutor.execute(() -> retryOne(orderId, claimToken));
        }
    }

    private void retryOne(String orderId, String claimToken) {
        Object raw = redis.opsForHash().get(SECKILL_PUBLISH_PAYLOAD_HASH, orderId);
        if (raw == null) {
            acknowledgeClaim(orderId, claimToken);
            return;
        }
        SeckillOrderMessage message = JSONUtil.toBean(String.valueOf(raw), SeckillOrderMessage.class);
        try {
            kafkaTemplate.send(ordersTopic, orderId, JSONUtil.toJsonStr(message)).get(10, TimeUnit.SECONDS);
            Long acknowledged = acknowledgeClaim(orderId, claimToken);
            if (acknowledged == null || acknowledged != 1L) {
                log.info("Ignoring late publish ACK from an expired claim. orderId={}", orderId);
                metrics.increment("seckill.publish", "stale_ack_ignored");
                return;
            }
            orderStatusService.transition(message.getOrderId(), SeckillOrderState.PROCESSING);
            metrics.increment("seckill.publish", "retry_success");
        } catch (Exception e) {
            int attempts = message.getRetryCount() == null ? 1 : message.getRetryCount() + 1;
            if (attempts >= maxPublishAttempts) {
                message.setRetryCount(attempts);
                publishDltAndCompensate(message, claimToken, e);
            } else {
                requeueClaim(message, claimToken, e);
            }
        }
    }

    private void publishDltAndCompensate(SeckillOrderMessage message, String claimToken, Exception error) {
        String orderId = String.valueOf(message.getOrderId());
        if (!claimToken.equals(redis.opsForHash().get(SECKILL_PUBLISH_CLAIM_HASH, orderId))) {
            metrics.increment("seckill.publish", "stale_dlt_ignored");
            return;
        }
        try {
            message.setLastError(trim(error));
            kafkaTemplate.send(dltTopic, orderId, JSONUtil.toJsonStr(message))
                    .get(10, TimeUnit.SECONDS);
            boolean restored = compensationService.compensateFailureClaim(message,
                    "publish retries exhausted", claimToken);
            if (!restored) acknowledgeClaim(orderId, claimToken);
            metrics.increment("kafka.dlt", restored ? "published_and_compensated" : "published_noop_cleanup");
        } catch (Exception dltError) {
            requeueClaim(message, claimToken, dltError);
            log.error("Failed to publish seckill DLT. orderId={}", message.getOrderId(), dltError);
        }
    }

    private void requeueClaim(SeckillOrderMessage message, String claimToken, Throwable error) {
        int attempts = message.getRetryCount() == null ? 1 : message.getRetryCount() + 1;
        message.setRetryCount(attempts);
        message.setLastError(trim(error));
        String orderId = String.valueOf(message.getOrderId());
        Long requeued = redis.execute(REQUEUE_SCRIPT, java.util.Arrays.asList(SECKILL_PUBLISH_PAYLOAD_HASH,
                        SECKILL_PUBLISH_RETRY_ZSET, SECKILL_PUBLISH_PROCESSING_ZSET,
                        SECKILL_PUBLISH_CLAIM_HASH), orderId, JSONUtil.toJsonStr(message),
                String.valueOf(System.currentTimeMillis() + retryDelayMillis(attempts)), claimToken);
        metrics.increment("seckill.publish", requeued != null && requeued == 1L
                ? "scheduled_retry" : "stale_requeue_ignored");
    }

    private Long acknowledgeClaim(String orderId, String claimToken) {
        return redis.execute(ACK_SCRIPT, java.util.Arrays.asList(SECKILL_PUBLISH_PAYLOAD_HASH,
                SECKILL_PUBLISH_RETRY_ZSET, SECKILL_PUBLISH_PROCESSING_ZSET,
                SECKILL_PUBLISH_CLAIM_HASH), orderId, claimToken);
    }

    @PreDestroy
    public void shutdown() {
        publishExecutor.shutdown();
    }

    private long retryDelayMillis(int attempts) {
        return Math.min(60_000L, 1_000L << Math.min(attempts, 6));
    }

    private String trim(Throwable error) {
        if (error == null || error.getMessage() == null) return null;
        String message = error.getMessage();
        return message.length() > 256 ? message.substring(0, 256) : message;
    }

    private long retryBacklog() {
        Long retry = redis.opsForZSet().zCard(SECKILL_PUBLISH_RETRY_ZSET);
        Long processing = redis.opsForZSet().zCard(SECKILL_PUBLISH_PROCESSING_ZSET);
        return (retry == null ? 0 : retry) + (processing == null ? 0 : processing);
    }

    private static DefaultRedisScript<Long> longScript(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static DefaultRedisScript<List> listScript(String path) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(List.class);
        return script;
    }
}
