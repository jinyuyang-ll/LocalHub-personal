package com.hmdp.controller.api;

import com.hmdp.annotation.RateLimit;
import com.hmdp.annotation.RateLimitType;
import com.hmdp.dto.Result;
import com.hmdp.entity.Reservation;
import com.hmdp.service.IReservationService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/reservations")
public class ApiReservationController {

    @Resource
    private IReservationService reservationService;

    @PostMapping
    @RateLimit(key = "api:reservation:create", limit = 10, windowSeconds = 60, type = RateLimitType.USER)
    public Result create(@RequestBody Reservation reservation) {
        return reservationService.createReservation(reservation);
    }

    @GetMapping("/{id}")
    public Result query(@PathVariable("id") Long id) {
        return reservationService.queryReservation(id);
    }

    @GetMapping("/me")
    public Result mine(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return reservationService.queryMyReservations(current);
    }

    @PostMapping("/{id}/cancel")
    public Result cancel(@PathVariable("id") Long id) {
        return reservationService.cancelReservation(id);
    }
}
