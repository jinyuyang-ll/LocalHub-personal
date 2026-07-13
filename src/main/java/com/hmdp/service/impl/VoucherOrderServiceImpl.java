package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.SeckillOrderMessage;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IOutboxEventService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_STATUS_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_STATUS_TTL;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private IOutboxEventService outboxEventService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;

    @Value("${localhub.seckill.queue:redis-stream}")
    private String seckillQueue;

    @Value("${localhub.kafka.topics.seckill-orders:localhub.seckill.orders}")
    private String seckillOrdersTopic;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    private static final DefaultRedisScript<Long> SECKILL_KAFKA_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);

        SECKILL_KAFKA_SCRIPT = new DefaultRedisScript<>();
        SECKILL_KAFKA_SCRIPT.setLocation(new ClassPathResource("seckill_kafka.lua"));
        SECKILL_KAFKA_SCRIPT.setResultType(Long.class);
    }


    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    private volatile boolean running = true;

    @PostConstruct
    private void init() {
        if ("kafka".equalsIgnoreCase(seckillQueue)) {
            log.info("Seckill order queue is Kafka, Redis Stream consumer is disabled.");
            return;
        }
        String redisVersion = stringRedisTemplate.execute((RedisCallback<String>) connection -> {
            Properties info = connection.serverCommands().info("server");
            return info == null ? null : info.getProperty("redis_version");
        });
        if (isRedisVersionBefore5(redisVersion)) {
            log.warn("Redis version {} does not support Stream commands. Seckill order consumer is disabled.", redisVersion);
            return;
        }
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private boolean isRedisVersionBefore5(String version) {
        if (version == null || version.trim().isEmpty()) {
            return false;
        }
        try {
            return Integer.parseInt(version.split("\\.")[0]) < 5;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @PreDestroy
    public void destroy() {
        running = false;
        SECKILL_ORDER_EXECUTOR.shutdownNow();
    }

    private boolean shouldStopConsumer(Exception e) {
        return !running
                || Thread.currentThread().isInterrupted()
                || (e.getMessage() != null && e.getMessage().contains("LettuceConnectionFactory was destroyed"));
    }

    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    // 1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
                    );
                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 如果为null，说明没有消息，继续下一次循环
                        continue;
                    }
                    // 解析数据
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 3.创建订单
                    createVoucherOrder(voucherOrder);
                    // 4.确认消息 XACK
                    stringRedisTemplate.opsForStream().acknowledge("stream.orders", "g1", record.getId());
                } catch (Exception e) {
                    if (shouldStopConsumer(e)) {
                        log.info("Seckill Redis Stream consumer stopped.");
                        break;
                    }
                    log.error("处理订单异常", e);
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    // 1.获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create("stream.orders", ReadOffset.from("0"))
                    );
                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 如果为null，说明没有异常消息，结束循环
                        break;
                    }
                    // 解析数据
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 3.创建订单
                    createVoucherOrder(voucherOrder);
                    // 4.确认消息 XACK
                    stringRedisTemplate.opsForStream().acknowledge("stream.orders", "g1", record.getId());
                } catch (Exception e) {
                    if (shouldStopConsumer(e)) {
                        log.info("Seckill Redis Stream pending-list consumer stopped.");
                        break;
                    }
                    log.error("处理订单异常", e);
                }
            }
        }
    }

    /*private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            while (true){
                try {
                    // 1.获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 2.创建订单
                    createVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }*/

    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        String statusKey = SECKILL_ORDER_STATUS_KEY + voucherOrder.getId();
        boolean[] created = {false};
        // 创建锁对象
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        // 尝试获取锁
        boolean isLock = redisLock.tryLock();
        // 判断
        if (!isLock) {
            // 获取锁失败，直接返回失败或者重试
            log.error("不允许重复下单！");
            return;
        }

        try {
            transactionTemplate.executeWithoutResult(status -> {
                // 5.1.查询订单
                int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
                // 5.2.判断是否存在
                if (count > 0) {
                    // 用户已经购买过了
                    log.error("不允许重复下单！");
                    stringRedisTemplate.opsForValue().set(statusKey, "DUPLICATE", SECKILL_ORDER_STATUS_TTL, TimeUnit.MINUTES);
                    return;
                }

                // 6.扣减库存
                boolean success = seckillVoucherService.update()
                        .setSql("stock = stock - 1") // set stock = stock - 1
                        .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
                        .update();
                if (!success) {
                    // 扣减失败
                    log.error("库存不足！");
                    stringRedisTemplate.opsForValue().set(statusKey, "FAILED", SECKILL_ORDER_STATUS_TTL, TimeUnit.MINUTES);
                    return;
                }

                // 7.创建订单
                save(voucherOrder);
                outboxEventService.createEvent(
                        "VoucherOrder",
                        voucherOrder.getId(),
                        "VOUCHER_ORDER_CREATED",
                        "localhub.order.events",
                        buildOrderPayload(voucherOrder)
                );
                created[0] = true;
            });
            if (created[0]) {
                stringRedisTemplate.opsForValue().set(statusKey, "SUCCESS", SECKILL_ORDER_STATUS_TTL, TimeUnit.MINUTES);
            }
        } finally {
            // 释放锁
            redisLock.unlock();
        }
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        boolean kafkaMode = "kafka".equalsIgnoreCase(seckillQueue);
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                kafkaMode ? SECKILL_KAFKA_SCRIPT : SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        int r = result == null ? -1 : result.intValue();
        // 2.判断结果是否为0
        if (r != 0) {
            // 2.1.不为0 ，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : r == 2 ? "不能重复下单" : "秒杀请求处理失败");
        }
        if (kafkaMode) {
            publishSeckillOrder(orderId, userId, voucherId);
        }
        // 3.返回订单id
        return Result.ok(orderId);
    }

    private void publishSeckillOrder(long orderId, Long userId, Long voucherId) {
        SeckillOrderMessage message = new SeckillOrderMessage();
        message.setOrderId(orderId);
        message.setUserId(userId);
        message.setVoucherId(voucherId);
        try {
            kafkaTemplate.send(seckillOrdersTopic, String.valueOf(orderId), JSONUtil.toJsonStr(message)).get();
        } catch (Exception e) {
            stringRedisTemplate.opsForValue().set(
                    SECKILL_ORDER_STATUS_KEY + orderId,
                    "FAILED",
                    SECKILL_ORDER_STATUS_TTL,
                    TimeUnit.MINUTES
            );
            log.error("Failed to publish seckill order to Kafka. orderId={}", orderId, e);
            throw new IllegalStateException("Failed to publish seckill order", e);
        }
    }

    @Override
    public Result queryOrderStatus(Long orderId) {
        VoucherOrder voucherOrder = getById(orderId);
        if (voucherOrder == null) {
            String status = stringRedisTemplate.opsForValue().get(SECKILL_ORDER_STATUS_KEY + orderId);
            return Result.ok(status == null ? "PROCESSING" : status);
        }
        Long userId = UserHolder.getUser().getId();
        if (!voucherOrder.getUserId().equals(userId)) {
            return Result.fail("无权查看该订单");
        }
        return Result.ok(voucherOrder.getStatus());
    }

    @Override
    public Result queryOrderById(Long orderId) {
        VoucherOrder voucherOrder = getById(orderId);
        if (voucherOrder == null) {
            return Result.fail("订单不存在或正在处理中");
        }
        Long userId = UserHolder.getUser().getId();
        if (!voucherOrder.getUserId().equals(userId)) {
            return Result.fail("无权查看该订单");
        }
        return Result.ok(voucherOrder);
    }

    @Override
    @Transactional
    public Result payOrder(Long orderId) {
        VoucherOrder voucherOrder = getById(orderId);
        if (voucherOrder == null) {
            return Result.fail("订单不存在");
        }
        Long userId = UserHolder.getUser().getId();
        if (!voucherOrder.getUserId().equals(userId)) {
            return Result.fail("无权操作该订单");
        }
        if (voucherOrder.getStatus() == null || voucherOrder.getStatus() != 1) {
            return Result.fail("订单当前状态不可支付");
        }

        boolean success = lambdaUpdate()
                .eq(VoucherOrder::getId, orderId)
                .eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getStatus, 1)
                .set(VoucherOrder::getStatus, 2)
                .set(VoucherOrder::getPayTime, LocalDateTime.now())
                .update();
        if (!success) {
            return Result.fail("订单支付失败，请刷新后重试");
        }
        voucherOrder.setStatus(2);
        voucherOrder.setPayTime(LocalDateTime.now());
        outboxEventService.createEvent(
                "VoucherOrder",
                orderId,
                "VOUCHER_ORDER_PAID",
                "localhub.order.events",
                buildOrderPayload(voucherOrder)
        );
        return Result.ok();
    }

    @Override
    @Transactional
    public Result cancelOrder(Long orderId) {
        VoucherOrder voucherOrder = getById(orderId);
        if (voucherOrder == null) {
            return Result.fail("订单不存在");
        }
        Long userId = UserHolder.getUser().getId();
        if (!voucherOrder.getUserId().equals(userId)) {
            return Result.fail("无权操作该订单");
        }
        if (voucherOrder.getStatus() == null || voucherOrder.getStatus() != 1) {
            return Result.fail("订单当前状态不可取消");
        }

        boolean success = lambdaUpdate()
                .eq(VoucherOrder::getId, orderId)
                .eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getStatus, 1)
                .set(VoucherOrder::getStatus, 4)
                .update();
        if (!success) {
            return Result.fail("订单取消失败，请刷新后重试");
        }
        voucherOrder.setStatus(4);
        outboxEventService.createEvent(
                "VoucherOrder",
                orderId,
                "VOUCHER_ORDER_CANCELLED",
                "localhub.order.events",
                buildOrderPayload(voucherOrder)
        );
        return Result.ok();
    }

    @Override
    @Scheduled(fixedDelay = 60_000)
    public void closeExpiredUnpaidOrders() {
        LocalDateTime expireTime = LocalDateTime.now().minusMinutes(15);
        List<VoucherOrder> expiredOrders = lambdaQuery()
                .eq(VoucherOrder::getStatus, 1)
                .lt(VoucherOrder::getCreateTime, expireTime)
                .list();
        for (VoucherOrder order : expiredOrders) {
            boolean success = lambdaUpdate()
                    .eq(VoucherOrder::getId, order.getId())
                    .eq(VoucherOrder::getStatus, 1)
                    .set(VoucherOrder::getStatus, 4)
                    .update();
            if (success) {
                order.setStatus(4);
                outboxEventService.createEvent(
                        "VoucherOrder",
                        order.getId(),
                        "VOUCHER_ORDER_CLOSED",
                        "localhub.order.events",
                        buildOrderPayload(order)
                );
            }
        }
        if (!expiredOrders.isEmpty()) {
            log.info("Closed {} expired unpaid voucher orders before {}", expiredOrders.size(), expireTime);
        }
    }

    private String buildOrderPayload(VoucherOrder order) {
        return String.format(
                "{\"orderId\":%d,\"userId\":%d,\"voucherId\":%d,\"status\":%d}",
                order.getId(), order.getUserId(), order.getVoucherId(), order.getStatus()
        );
    }

    /*@Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        int r = result.intValue();
        // 2.判断结果是否为0
        if (r != 0) {
            // 2.1.不为0 ，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        // 2.2.为0 ，有购买资格，把下单信息保存到阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        // 2.3.订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 2.4.用户id
        voucherOrder.setUserId(userId);
        // 2.5.代金券id
        voucherOrder.setVoucherId(voucherId);
        // 2.6.放入阻塞队列
        orderTasks.add(voucherOrder);

        // 3.返回订单id
        return Result.ok(orderId);
    }*/
    /*@Override
    public Result seckillVoucher(Long voucherId) {
        // 1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀尚未开始！");
        }
        // 3.判断秒杀是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀已经结束！");
        }
        // 4.判断库存是否充足
        if (voucher.getStock() < 1) {
            // 库存不足
            return Result.fail("库存不足！");
        }

        return createVoucherOrder(voucherId);
    }



    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 5.一人一单
        Long userId = UserHolder.getUser().getId();

        // 创建锁对象
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        // 尝试获取锁
        boolean isLock = redisLock.tryLock();
        // 判断
        if(!isLock){
            // 获取锁失败，直接返回失败或者重试
            return Result.fail("不允许重复下单！");
        }

        try {
            // 5.1.查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            // 5.2.判断是否存在
            if (count > 0) {
                // 用户已经购买过了
                return Result.fail("用户已经购买过一次！");
            }

            // 6.扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1") // set stock = stock - 1
                    .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
                    .update();
            if (!success) {
                // 扣减失败
                return Result.fail("库存不足！");
            }

            // 7.创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            // 7.1.订单id
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            // 7.2.用户id
            voucherOrder.setUserId(userId);
            // 7.3.代金券id
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);

            // 7.返回订单id
            return Result.ok(orderId);
        } finally {
            // 释放锁
            redisLock.unlock();
        }

    }*/
    /*@Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 5.一人一单
        Long userId = UserHolder.getUser().getId();

        // 创建锁对象
        SimpleRedisLock redisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        // 尝试获取锁
        boolean isLock = redisLock.tryLock(1200);
        // 判断
        if(!isLock){
            // 获取锁失败，直接返回失败或者重试
            return Result.fail("不允许重复下单！");
        }

        try {
            // 5.1.查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            // 5.2.判断是否存在
            if (count > 0) {
                // 用户已经购买过了
                return Result.fail("用户已经购买过一次！");
            }

            // 6.扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1") // set stock = stock - 1
                    .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
                    .update();
            if (!success) {
                // 扣减失败
                return Result.fail("库存不足！");
            }

            // 7.创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            // 7.1.订单id
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            // 7.2.用户id
            voucherOrder.setUserId(userId);
            // 7.3.代金券id
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);

            // 7.返回订单id
            return Result.ok(orderId);
        } finally {
            // 释放锁
            redisLock.unlock();
        }

    }*/

    /*@Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 5.一人一单
        Long userId = UserHolder.getUser().getId();

        synchronized (userId.toString().intern()) {
            // 5.1.查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            // 5.2.判断是否存在
            if (count > 0) {
                // 用户已经购买过了
                return Result.fail("用户已经购买过一次！");
            }

            // 6.扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1") // set stock = stock - 1
                    .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
                    .update();
            if (!success) {
                // 扣减失败
                return Result.fail("库存不足！");
            }

            // 7.创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            // 7.1.订单id
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            // 7.2.用户id
            voucherOrder.setUserId(userId);
            // 7.3.代金券id
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);

            // 7.返回订单id
            return Result.ok(orderId);
        }
    }*/
}
