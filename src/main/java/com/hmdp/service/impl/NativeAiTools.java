package com.hmdp.service.impl;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Component
public class NativeAiTools {
    @Resource private AiBusinessTools tools;
    @Resource private AiToolGuard toolGuard;

    @Tool(name = "queryOrderStatus", value = "查询当前登录用户的订单状态")
    public String queryOrderStatus(@P("订单编号") Long orderId) { return tools.queryOrderStatus(orderId); }

    @Tool(name = "queryShop", value = "查询店铺详情")
    public String queryShop(@P("店铺编号") Long shopId) { return tools.queryShop(shopId); }

    @Tool(name = "queryVouchersByShop", value = "实时查询指定店铺当前可用的优惠券")
    public String queryVouchersByShop(@P("店铺编号") Long shopId) {
        return tools.queryVouchersByShop(shopId);
    }

    @Tool(name = "createReservationPreview", value = "创建预约预览，不会最终写入，必须经过用户确认")
    public String createReservationPreview(@P("店铺编号") Long shopId,
                                           @P("ISO-8601格式预约时间，例如2026-09-20T18:30:00") String reserveTime) {
        com.hmdp.dto.AiReservationRequest request = new com.hmdp.dto.AiReservationRequest();
        request.setShopId(shopId); request.setReserveTime(LocalDateTime.parse(reserveTime));
        return cn.hutool.json.JSONUtil.toJsonStr(tools.previewReservation(request));
    }

    @Tool(name = "confirmReservation", value = "使用一次性确认令牌确认预约")
    public String confirmReservation(@P("预约预览返回的一次性确认令牌") String confirmationToken) {
        toolGuard.requireExplicitConfirmation();
        com.hmdp.dto.AiReservationRequest request = new com.hmdp.dto.AiReservationRequest();
        request.setConfirmationToken(confirmationToken);
        return cn.hutool.json.JSONUtil.toJsonStr(tools.confirmReservation(request));
    }

    @Tool(name = "cancelReservationPreview", value = "取消尚未确认的预约预览")
    public String cancelReservationPreview(@P("预约预览返回的一次性确认令牌") String confirmationToken) {
        return cn.hutool.json.JSONUtil.toJsonStr(tools.cancelReservationPreview(confirmationToken));
    }
}
