package com.hmdp.service.impl;

import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AiAuditLogger {
    public void record(String tool, String outcome) {
        Long userId = UserHolder.getUser() == null ? null : UserHolder.getUser().getId();
        log.info("ai_tool_audit tool={} userId={} outcome={}", tool, userId, outcome);
    }
}
