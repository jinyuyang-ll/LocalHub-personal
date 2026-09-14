package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.config.LocalHubMetrics;
import com.hmdp.dto.Result;
import com.hmdp.dto.SeckillOrderMessage;
import com.hmdp.enums.SeckillOrderState;
import com.hmdp.exception.BusinessException;
import com.hmdp.exception.ErrorCode;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Service
public class SeckillService {
    private static final DefaultRedisScript<Long> STREAM_SCRIPT = script("seckill.lua");
    private static final DefaultRedisScript<Long> KAFKA_SCRIPT = script("seckill_kafka.lua");

    @Resource private RedisIdWorker idWorker;
    @Resource private StringRedisTemplate redis;
    @Resource private KafkaTemplate<String, String> kafkaTemplate;
    @Resource private ObjectProvider<SeckillKafkaRecoveryService> recoveryProvider;
    @Resource private SeckillCompensationService compensationService;
    @Resource private OrderStatusService orderStatusService;
    @Resource private LocalHubMetrics metrics;

    @Value("${localhub.seckill.queue:redis-stream}") private String queue;
    @Value("${localhub.kafka.topics.seckill-orders:localhub.seckill.orders}") private String ordersTopic;

    public Result seckill(Long voucherId) {
        if (voucherId == null || voucherId <= 0) throw new BusinessException(ErrorCode.INVALID_PARAMETER);
        if (UserHolder.getUser() == null) throw new BusinessException(ErrorCode.FORBIDDEN, "请先登录");
        Long userId = UserHolder.getUser().getId();
        long orderId = idWorker.nextId("order");
        boolean kafka = "kafka".equalsIgnoreCase(queue);
        Long result = redis.execute(kafka ? KAFKA_SCRIPT : STREAM_SCRIPT,
                kafka ? kafkaKeys(voucherId, orderId) : streamKeys(voucherId, orderId),
                voucherId.toString(), userId.toString(), String.valueOf(orderId),
                String.valueOf(SECKILL_PENDING_TTL_SECONDS));
        int code = result == null ? -1 : result.intValue();
        if (code != 0) {
            metrics.increment("seckill.accept", code == 1 ? "sold_out" : code == 2 ? "duplicate" : "failed");
            if (code == 1) throw new BusinessException(ErrorCode.STOCK_EMPTY);
            if (code == 2) throw new BusinessException(ErrorCode.ORDER_EXIST);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "秒杀请求处理失败");
        }
        if (kafka) publish(orderId, userId, voucherId);
        metrics.increment("seckill.accept", "success");
        return Result.ok(orderId);
    }

    private void publish(long orderId, Long userId, Long voucherId) {
        SeckillOrderMessage message = new SeckillOrderMessage();
        message.setOrderId(orderId); message.setUserId(userId); message.setVoucherId(voucherId); message.setRetryCount(0);
        try {
            kafkaTemplate.send(ordersTopic, String.valueOf(orderId), JSONUtil.toJsonStr(message))
                    .get(10, TimeUnit.SECONDS);
            orderStatusService.transition(orderId, SeckillOrderState.PROCESSING);
            metrics.increment("seckill.publish", "success");
        } catch (Exception error) {
            SeckillKafkaRecoveryService recovery = recoveryProvider.getIfAvailable();
            if (recovery == null) {
                compensationService.compensateFailure(message, "Kafka recovery service unavailable");
                throw new BusinessException(ErrorCode.RETRYABLE_ERROR, "Kafka补偿服务不可用", error);
            }
            try {
                recovery.schedulePublish(message, error);
            } catch (RuntimeException scheduleError) {
                compensationService.compensateFailure(message, "Kafka publish and durable retry both failed");
                throw new BusinessException(ErrorCode.RETRYABLE_ERROR, "Kafka发送失败，已回滚秒杀资格", scheduleError);
            }
            log.warn("Kafka publish failed; durable retry scheduled. orderId={}", orderId, error);
        }
    }

    private java.util.List<String> kafkaKeys(Long voucherId, long orderId) {
        return Arrays.asList(SECKILL_STOCK_KEY + voucherId, SECKILL_ORDER_KEY + voucherId,
                SECKILL_ORDER_STATUS_KEY + orderId, SECKILL_RESERVATION_KEY + orderId,
                SECKILL_ORDER_OWNER_KEY + orderId);
    }

    private java.util.List<String> streamKeys(Long voucherId, long orderId) {
        return Arrays.asList(SECKILL_STOCK_KEY + voucherId, SECKILL_ORDER_KEY + voucherId,
                SECKILL_ORDER_STATUS_KEY + orderId, SECKILL_RESERVATION_KEY + orderId,
                STREAM_ORDERS_KEY, SECKILL_ORDER_OWNER_KEY + orderId);
    }

    private static DefaultRedisScript<Long> script(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }
}
