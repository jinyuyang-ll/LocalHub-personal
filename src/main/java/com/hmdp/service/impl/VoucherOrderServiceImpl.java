package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/** Voucher-order facade; domain responsibilities live in dedicated services. */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    @Resource private SeckillService seckillService;
    @Resource private OrderStatusService orderStatusService;
    @Resource private OrderPaymentService orderPaymentService;
    @Resource private OrderCancelService orderCancelService;
    @Resource private OrderCloseService orderCloseService;

    @Override public Result seckillVoucher(Long voucherId) { return seckillService.seckill(voucherId); }
    @Override public Result queryOrderStatus(Long orderId) { return orderStatusService.queryStatus(orderId); }
    @Override public Result queryOrderById(Long orderId) { return orderStatusService.queryOrder(orderId); }
    @Override public Result payOrder(Long orderId) { return orderPaymentService.pay(orderId); }
    @Override public Result cancelOrder(Long orderId) { return orderCancelService.cancel(orderId); }
    @Override public void closeExpiredUnpaidOrders() {
        orderCloseService.closeExpired(LocalDateTime.now().minusMinutes(15));
    }
}
