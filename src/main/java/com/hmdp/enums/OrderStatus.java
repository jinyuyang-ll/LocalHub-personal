package com.hmdp.enums;

public enum OrderStatus {
    UNPAID(1), PAID(2), USED(3), CANCELLED(4), REFUNDING(5), REFUNDED(6);

    private final int code;

    OrderStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public boolean canTransitionTo(OrderStatus target) {
        return this == UNPAID && (target == PAID || target == CANCELLED);
    }

    public static OrderStatus fromCode(Integer code) {
        if (code == null) return null;
        for (OrderStatus status : values()) if (status.code == code) return status;
        return null;
    }
}
