package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.AiReservationRequest;
import com.hmdp.config.LocalHubMetrics;
import com.hmdp.dto.Result;
import com.hmdp.entity.Reservation;
import com.hmdp.entity.Shop;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IReservationService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class AiBusinessTools {

    private static final String CONFIRM_KEY = "ai:reservation:confirm:";

    @Resource private IShopService shopService;
    @Resource private IVoucherOrderService voucherOrderService;
    @Resource private IReservationService reservationService;
    @Resource(name = "stringRedisTemplate") private StringRedisTemplate redisTemplate;
    @Resource private AiToolGuard toolGuard;
    @Resource private LocalHubMetrics metrics;

    public String queryShop(Long shopId) {
        requireAllowed("queryShop");
        Shop shop = shopService.getById(shopId);
        return shop == null ? "未找到店铺 " + shopId : JSONUtil.toJsonStr(shop);
    }

    public String queryOrderStatus(Long orderId) {
        requireAllowed("queryOrderStatus");
        requireLogin();
        Result result = voucherOrderService.queryOrderStatus(orderId);
        metrics.increment("ai.tool", "query_order");
        return result.getSuccess() ? "订单 " + orderId + " 当前状态：" + result.getData() : result.getErrorMsg();
    }

    public Result previewReservation(AiReservationRequest request) {
        requireAllowed("createReservation");
        Long userId = requireLogin();
        if (request.getShopId() == null || request.getReserveTime() == null) {
            return Result.fail("店铺和预约时间不能为空");
        }
        if (request.getReserveTime().isBefore(LocalDateTime.now())) {
            return Result.fail("预约时间不能早于当前时间");
        }
        Shop shop = shopService.getById(request.getShopId());
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        Reservation reservation = new Reservation()
                .setUserId(userId)
                .setShopId(request.getShopId())
                .setReserveTime(request.getReserveTime())
                .setRemark(request.getRemark());
        redisTemplate.opsForValue().set(CONFIRM_KEY + token, JSONUtil.toJsonStr(reservation), 5, TimeUnit.MINUTES);
        metrics.increment("ai.reservation", "previewed");
        return Result.ok(new ReservationPreview(token, shop.getName(), request.getReserveTime(), request.getRemark()));
    }

    public Result confirmReservation(AiReservationRequest request) {
        requireAllowed("createReservation");
        Long userId = requireLogin();
        if (StrUtil.isBlank(request.getConfirmationToken())) {
            return Result.fail("确认令牌不能为空");
        }
        String key = CONFIRM_KEY + request.getConfirmationToken();
        String payload = redisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(payload)) {
            return Result.fail("确认令牌无效或已过期");
        }
        Reservation reservation = JSONUtil.toBean(payload, Reservation.class);
        if (!userId.equals(reservation.getUserId())) {
            return Result.fail("确认令牌不属于当前用户");
        }
        if (!Boolean.TRUE.equals(redisTemplate.delete(key))) {
            metrics.increment("ai.reservation", "duplicate_confirm");
            return Result.fail("请勿重复确认预约");
        }
        Result result = reservationService.createReservation(reservation);
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
