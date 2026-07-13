package com.hmdp.controller.api;

import com.hmdp.annotation.RateLimit;
import com.hmdp.annotation.RateLimitType;
import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api")
public class ApiVoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @PostMapping("/seckill-vouchers/{voucherId}/orders")
    @RateLimit(key = "api:seckill-voucher:order", limit = 5, windowSeconds = 10, type = RateLimitType.USER)
    public Result seckill(@PathVariable("voucherId") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    @GetMapping("/voucher-orders/{orderId}/status")
    public Result status(@PathVariable("orderId") Long orderId) {
        return voucherOrderService.queryOrderStatus(orderId);
    }

    @GetMapping("/voucher-orders/{orderId}")
    public Result order(@PathVariable("orderId") Long orderId) {
        return voucherOrderService.queryOrderById(orderId);
    }

    @PostMapping("/voucher-orders/{orderId}/pay")
    public Result pay(@PathVariable("orderId") Long orderId) {
        return voucherOrderService.payOrder(orderId);
    }

    @PostMapping("/voucher-orders/{orderId}/cancel")
    public Result cancel(@PathVariable("orderId") Long orderId) {
        return voucherOrderService.cancelOrder(orderId);
    }
}
