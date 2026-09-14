package com.hmdp.controller.api;

import com.hmdp.annotation.RateLimit;
import com.hmdp.annotation.RateLimitType;
import com.hmdp.dto.Result;
import com.hmdp.entity.Reservation;
import com.hmdp.service.IReservationService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import javax.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/reservations")
@Validated
public class ApiReservationController {

    @Resource
    private IReservationService reservationService;

    @PostMapping
    @RateLimit(key = "api:reservation:create", limit = 10, windowSeconds = 60, type = RateLimitType.USER)
    public Result create(@Valid @RequestBody Reservation reservation) {
        return reservationService.createReservation(reservation);
    }

    @GetMapping("/{id}")
    public Result query(@PathVariable("id") @Positive Long id) {
        return reservationService.queryReservation(id);
    }

    @GetMapping("/me")
    public Result mine(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return reservationService.queryMyReservations(current);
    }

    @PostMapping("/{id}/cancel")
    public Result cancel(@PathVariable("id") @Positive Long id) {
        return reservationService.cancelReservation(id);
    }
}
