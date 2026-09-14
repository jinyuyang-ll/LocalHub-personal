package com.hmdp.service.impl;

import org.springframework.stereotype.Component;
import com.hmdp.exception.BusinessException;
import com.hmdp.exception.ErrorCode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class AiToolGuard {

    private final Set<String> allowedTools = new HashSet<>(Arrays.asList(
            "queryShop",
            "queryVouchersByShop",
            "queryOrderStatus",
            "createReservationPreview",
            "confirmReservation",
            "cancelReservationPreview"
    ));

    public boolean isAllowed(String toolName) {
        return allowedTools.contains(toolName);
    }

    private static final ThreadLocal<String> USER_TURN = new ThreadLocal<>();

    public void beginUserTurn(String message) {
        USER_TURN.set(message == null ? "" : message);
    }

    public void endUserTurn() {
        USER_TURN.remove();
    }

    public void requireExplicitConfirmation() {
        String message = USER_TURN.get();
        String normalized = message == null ? "" : message.toLowerCase();
        if (!normalized.contains("确认") && !normalized.contains("confirm")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "必须由用户明确确认后才能创建预约");
        }
    }
}
