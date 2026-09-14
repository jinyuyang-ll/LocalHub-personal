package com.hmdp.exception;

public enum ErrorCode {
    INVALID_PARAMETER("INVALID_PARAMETER", "请求参数不正确"),
    STOCK_EMPTY("STOCK_EMPTY", "库存不足"),
    ORDER_EXIST("ORDER_EXIST", "不能重复下单"),
    ORDER_NOT_FOUND("ORDER_NOT_FOUND", "订单不存在"),
    ORDER_STATE_INVALID("ORDER_STATE_INVALID", "订单状态不允许当前操作"),
    FORBIDDEN("FORBIDDEN", "无权执行该操作"),
    TOKEN_INVALID("TOKEN_INVALID", "令牌无效或已过期"),
    RETRYABLE_ERROR("RETRYABLE_ERROR", "系统繁忙，请稍后重试"),
    INTERNAL_ERROR("INTERNAL_ERROR", "服务器异常");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
}
