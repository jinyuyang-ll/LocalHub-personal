package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Reservation;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ReservationMapper;
import com.hmdp.service.IReservationService;
import com.hmdp.service.IShopService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class ReservationServiceImpl extends ServiceImpl<ReservationMapper, Reservation> implements IReservationService {

    private static final int STATUS_RESERVED = 1;
    private static final int STATUS_CANCELLED = 2;

    @Resource
    private IShopService shopService;

    @Override
    @Transactional
    public Result createReservation(Reservation reservation) {
        if (reservation.getShopId() == null || reservation.getReserveTime() == null) {
            return Result.fail("店铺和预约时间不能为空");
        }
        if (reservation.getReserveTime().isBefore(LocalDateTime.now())) {
            return Result.fail("预约时间不能早于当前时间");
        }
        Shop shop = shopService.getById(reservation.getShopId());
        if (shop == null) {
            return Result.fail("店铺不存在");
        }

        reservation.setId(null);
        reservation.setUserId(UserHolder.getUser().getId());
        reservation.setStatus(STATUS_RESERVED);
        try {
            save(reservation);
        } catch (DuplicateKeyException e) {
            return Result.fail("请勿重复预约同一时间段");
        }
        return Result.ok(reservation.getId());
    }

    @Override
    public Result queryReservation(Long reservationId) {
        Reservation reservation = getById(reservationId);
        if (reservation == null) {
            return Result.fail("预约不存在");
        }
        Long userId = UserHolder.getUser().getId();
        if (!reservation.getUserId().equals(userId)) {
            return Result.fail("无权查看该预约");
        }
        return Result.ok(reservation);
    }

    @Override
    public Result queryMyReservations(Integer current) {
        Long userId = UserHolder.getUser().getId();
        Page<Reservation> page = lambdaQuery()
                .eq(Reservation::getUserId, userId)
                .orderByDesc(Reservation::getCreateTime)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }

    @Override
    @Transactional
    public Result cancelReservation(Long reservationId) {
        Reservation reservation = getById(reservationId);
        if (reservation == null) {
            return Result.fail("预约不存在");
        }
        Long userId = UserHolder.getUser().getId();
        if (!reservation.getUserId().equals(userId)) {
            return Result.fail("无权取消该预约");
        }
        if (reservation.getStatus() == null || reservation.getStatus() != STATUS_RESERVED) {
            return Result.fail("当前预约状态不可取消");
        }
        boolean success = lambdaUpdate()
                .eq(Reservation::getId, reservationId)
                .eq(Reservation::getUserId, userId)
                .eq(Reservation::getStatus, STATUS_RESERVED)
                .set(Reservation::getStatus, STATUS_CANCELLED)
                .update();
        return success ? Result.ok() : Result.fail("取消失败，请刷新后重试");
    }
}
