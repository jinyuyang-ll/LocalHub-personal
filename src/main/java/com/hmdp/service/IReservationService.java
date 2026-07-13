package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Reservation;

public interface IReservationService extends IService<Reservation> {

    Result createReservation(Reservation reservation);

    Result queryReservation(Long reservationId);

    Result queryMyReservations(Integer current);

    Result cancelReservation(Long reservationId);
}
