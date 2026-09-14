package com.hmdp.service.impl;

import com.hmdp.exception.BusinessException;
import com.hmdp.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class AiGuardrailService {
    public String sanitizeInput(String message) {
        if (message == null) return "";
        String cleaned = message
                .replaceAll("(?i)(ignore (all|previous) instructions|system prompt|developer message)", "[已过滤]")
                .replace("忽略之前的指令", "[已过滤]")
                .replace("忽略系统提示", "[已过滤]").trim();
        if (cleaned.length() > 2_000) throw new BusinessException(ErrorCode.INVALID_PARAMETER, "问题不能超过2000字");
        return cleaned;
    }

    public String sanitizeOutput(String answer) {
        if (answer == null) return "";
        return answer.replaceAll("(?i)(api[_ -]?key|authorization)\\s*[:=]\\s*\\S+", "$1=[已脱敏]");
    }
}
