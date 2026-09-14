package com.hmdp.controller.api;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Reservation;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IReservationService;
import com.hmdp.service.IUserService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users/me")
public class ApiUserCenterController {
    @Resource private IVoucherOrderService orderService;
    @Resource private IReservationService reservationService;
    @Resource private IUserService userService;

    @GetMapping("/summary")
    public Result summary() {
        UserDTO user = UserHolder.getUser();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("user", user);
        summary.put("orders", orderService.list(new QueryWrapper<VoucherOrder>()
                .eq("user_id", user.getId()).orderByDesc("create_time").last("LIMIT 20")));
        summary.put("reservations", reservationService.list(new QueryWrapper<Reservation>()
                .eq("user_id", user.getId()).orderByDesc("create_time").last("LIMIT 20")));
        Result sign = userService.signCount();
        summary.put("continuousSignDays", sign.getSuccess() ? sign.getData() : 0);
        return Result.ok(summary);
    }

    @PostMapping("/sign")
    public Result sign() { return userService.sign(); }
}
