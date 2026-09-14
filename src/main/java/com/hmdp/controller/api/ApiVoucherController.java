package com.hmdp.controller.api;

import com.hmdp.annotation.RateLimit;
import com.hmdp.annotation.RateLimitType;
import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.hmdp.service.IVoucherService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/vouchers")
@Validated
public class ApiVoucherController {

    @Resource
    private IVoucherService voucherService;

    @GetMapping("/shops/{shopId}")
    public Result queryVoucherOfShop(@PathVariable("shopId") @Positive Long shopId) {
        return voucherService.queryVoucherOfShop(shopId);
    }

    @PostMapping
    public Result addVoucher(@RequestBody Voucher voucher) {
        voucherService.save(voucher);
        return Result.ok(voucher.getId());
    }

    @PostMapping("/seckill")
    public Result addSeckillVoucher(@RequestBody Voucher voucher) {
        voucherService.addSeckillVoucher(voucher);
        return Result.ok(voucher.getId());
    }

    @PostMapping("/{voucherId}/receive")
    @RateLimit(key = "api:voucher:receive", limit = 10, windowSeconds = 60, type = RateLimitType.USER)
    public Result receiveVoucher(@PathVariable("voucherId") @Positive Long voucherId) {
        return voucherService.receiveVoucher(voucherId);
    }
}
