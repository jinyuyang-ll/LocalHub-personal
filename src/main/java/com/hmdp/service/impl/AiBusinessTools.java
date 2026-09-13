package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.AiReservationRequest;
import com.hmdp.config.LocalHubMetrics;
import com.hmdp.dto.Result;
import com.hmdp.entity.Reservation;
import com.hmdp.entity.Shop;
import com.hmdp.service.IReservationService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Component
public class AiBusinessTools {

    private static final String CONFIRM_KEY = "ai:reservation:confirm:";
    private static final String RESULT_KEY = "ai:reservation:result:";
    private static final String LOCK_KEY = "ai:reservation:lock:";
    private static final String STATE_KEY = "ai:reservation:state:";
    private static final String STATE_PREVIEWED = "PREVIEWED";
    private static final String STATE_CONFIRMING = "CONFIRMING";
    private static final String STATE_CONFIRMED = "CONFIRMED";
    private static final String STATE_CANCELLED = "CANCELLED";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    @Resource private IShopService shopService;
    @Resource private IVoucherOrderService voucherOrderService;
    @Resource private IVoucherService voucherService;
    @Resource private IReservationService reservationService;
    @Resource(name = "stringRedisTemplate") private StringRedisTemplate redisTemplate;
    @Resource private AiToolGuard toolGuard;
    @Resource private LocalHubMetrics metrics;

    public String queryShop(Long shopId) {
        requireAllowed("queryShop");
        if (shopId == null || shopId <= 0) throw new IllegalArgumentException("店铺编号必须为正整数");
        Result result = shopService.queryById(shopId);
        return result.getSuccess() ? JSONUtil.toJsonStr(result.getData()) : result.getErrorMsg();
    }

    public String queryOrderStatus(Long orderId) {
        requireAllowed("queryOrderStatus");
        requireLogin();
        if (orderId == null || orderId <= 0) throw new IllegalArgumentException("订单编号必须为正整数");
        Result result = voucherOrderService.queryOrderStatus(orderId);
        metrics.increment("ai.tool", "query_order");
        return result.getSuccess() ? "订单 " + orderId + " 当前状态：" + result.getData() : result.getErrorMsg();
    }

    public String queryVouchersByShop(Long shopId) {
        requireAllowed("queryVouchersByShop");
        if (shopId == null || shopId <= 0) throw new IllegalArgumentException("店铺编号必须为正整数");
        Result result = voucherService.queryVoucherOfShop(shopId);
        return result.getSuccess() ? JSONUtil.toJsonStr(result.getData()) : result.getErrorMsg();
    }

    public Result previewReservation(AiReservationRequest request) {
        requireAllowed("createReservationPreview");
        Long userId = requireLogin();
        if (request.getShopId() == null || request.getReserveTime() == null) {
            return Result.fail("店铺和预约时间不能为空");
        }
        if (request.getReserveTime().isBefore(LocalDateTime.now())) {
            return Result.fail("预约时间不能早于当前时间");
        }
        if (request.getRemark() != null && request.getRemark().length() > 200) return Result.fail("备注长度不能超过200字");
        Result shopResult = shopService.queryById(request.getShopId());
        if (!shopResult.getSuccess()) {
            return Result.fail("店铺不存在");
        }
        Shop shop = (Shop) shopResult.getData();
        String token = UUID.randomUUID().toString().replace("-", "");
        Reservation reservation = new Reservation()
                .setUserId(userId)
                .setShopId(request.getShopId())
                .setReserveTime(request.getReserveTime())
                .setRemark(request.getRemark());
        redisTemplate.opsForValue().set(CONFIRM_KEY + token, JSONUtil.toJsonStr(reservation), 5, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(STATE_KEY + token, STATE_PREVIEWED, 5, TimeUnit.MINUTES);
        metrics.increment("ai.reservation", "previewed");
        return Result.ok(new ReservationPreview(token, shop.getName(), request.getReserveTime(), request.getRemark()));
    }

    public Result confirmReservation(AiReservationRequest request) {
        requireAllowed("confirmReservation");
        Long userId = requireLogin();
        if (StrUtil.isBlank(request.getConfirmationToken())) {
            return Result.fail("确认令牌不能为空");
        }
        String token = request.getConfirmationToken().trim();
        if (!token.matches("[a-fA-F0-9]{32}")) return Result.fail("确认令牌格式错误");
        String completed = redisTemplate.opsForValue().get(RESULT_KEY + token);
        if (StrUtil.isNotBlank(completed)) return Result.ok(Long.valueOf(completed));

        String lockValue = UUID.randomUUID().toString();
        String lockKey = LOCK_KEY + token;
        if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, 30, TimeUnit.SECONDS))) {
            metrics.increment("ai.reservation", "duplicate_confirm");
            return Result.fail("预约正在确认，请勿重复提交");
        }
        try {
            completed = redisTemplate.opsForValue().get(RESULT_KEY + token);
            if (StrUtil.isNotBlank(completed)) return Result.ok(Long.valueOf(completed));
            String state = redisTemplate.opsForValue().get(STATE_KEY + token);
            if (!STATE_PREVIEWED.equals(state)) {
                return Result.fail(state == null ? "确认令牌无效或已超时" : "当前预约状态不可确认：" + state);
            }
            redisTemplate.opsForValue().set(STATE_KEY + token, STATE_CONFIRMING, 30, TimeUnit.SECONDS);
            Result result = confirmOnce(token, userId);
            if (result.getSuccess()) {
                redisTemplate.opsForValue().set(STATE_KEY + token, STATE_CONFIRMED, 24, TimeUnit.HOURS);
            } else {
                redisTemplate.opsForValue().set(STATE_KEY + token, STATE_PREVIEWED, 5, TimeUnit.MINUTES);
            }
            return result;
        } finally {
            redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey), lockValue);
        }
    }

    public Result cancelReservationPreview(String token) {
        requireAllowed("cancelReservationPreview");
        Long userId = requireLogin();
        if (StrUtil.isBlank(token) || !token.trim().matches("[a-fA-F0-9]{32}")) {
            return Result.fail("确认令牌格式错误");
        }
        token = token.trim();
        String lockValue = UUID.randomUUID().toString();
        String lockKey = LOCK_KEY + token;
        if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, 30, TimeUnit.SECONDS))) {
            return Result.fail("预约正在处理，请稍后重试");
        }
        try {
            String payload = redisTemplate.opsForValue().get(CONFIRM_KEY + token);
            if (StrUtil.isBlank(payload)) return Result.fail("预约预览不存在或已超时");
            Reservation reservation = JSONUtil.toBean(payload, Reservation.class);
            if (!userId.equals(reservation.getUserId())) return Result.fail("确认令牌不属于当前用户");
            if (!STATE_PREVIEWED.equals(redisTemplate.opsForValue().get(STATE_KEY + token))) {
                return Result.fail("当前预约状态不可取消");
            }
            redisTemplate.delete(CONFIRM_KEY + token);
            redisTemplate.opsForValue().set(STATE_KEY + token, STATE_CANCELLED, 5, TimeUnit.MINUTES);
            metrics.increment("ai.reservation", "cancelled_preview");
            return Result.ok();
        } finally {
            redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey), lockValue);
        }
    }

    private Result confirmOnce(String token, Long userId) {
        String key = CONFIRM_KEY + token;
        String payload = redisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(payload)) {
            return Result.fail("确认令牌无效或已过期");
        }
        Reservation reservation = JSONUtil.toBean(payload, Reservation.class);
        if (!userId.equals(reservation.getUserId())) {
            return Result.fail("确认令牌不属于当前用户");
        }
        Result result = reservationService.createReservation(reservation);
        if (!result.getSuccess()) {
            Reservation existing = reservationService.lambdaQuery()
                    .eq(Reservation::getUserId, userId)
                    .eq(Reservation::getShopId, reservation.getShopId())
                    .eq(Reservation::getReserveTime, reservation.getReserveTime())
                    .one();
            if (existing != null) result = Result.ok(existing.getId());
        }
        if (result.getSuccess()) {
            redisTemplate.opsForValue().set(RESULT_KEY + token, String.valueOf(result.getData()), 24, TimeUnit.HOURS);
            redisTemplate.delete(key);
        }
        metrics.increment("ai.reservation", result.getSuccess() ? "confirmed" : "failed");
        return result;
    }

    private void requireAllowed(String tool) {
        if (!toolGuard.isAllowed(tool)) throw new IllegalArgumentException("工具未授权：" + tool);
    }

    private Long requireLogin() {
        if (UserHolder.getUser() == null) throw new IllegalStateException("请先登录后使用该工具");
        return UserHolder.getUser().getId();
    }

    public static class ReservationPreview {
        public final String confirmationToken;
        public final String shopName;
        public final LocalDateTime reserveTime;
        public final String remark;

        public ReservationPreview(String confirmationToken, String shopName, LocalDateTime reserveTime, String remark) {
            this.confirmationToken = confirmationToken;
            this.shopName = shopName;
            this.reserveTime = reserveTime;
            this.remark = remark;
        }
    }
}
