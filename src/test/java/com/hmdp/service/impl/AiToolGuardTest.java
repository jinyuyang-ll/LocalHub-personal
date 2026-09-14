package com.hmdp.service.impl;

import org.junit.jupiter.api.Test;
import com.hmdp.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiToolGuardTest {

    private final AiToolGuard guard = new AiToolGuard();

    @Test
    void writeToolRequiresConfirmationInCurrentUserTurn() {
        guard.beginUserTurn("请帮我预约");
        try {
            assertThrows(BusinessException.class, guard::requireExplicitConfirmation);
        } finally {
            guard.endUserTurn();
        }
    }

    @Test
    void explicitConfirmationAllowsWriteTool() {
        guard.beginUserTurn("确认预约，令牌 abc123");
        try {
            assertDoesNotThrow(guard::requireExplicitConfirmation);
        } finally {
            guard.endUserTurn();
        }
    }
}
