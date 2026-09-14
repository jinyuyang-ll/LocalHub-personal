package com.hmdp.service.impl;

import com.hmdp.config.LocalHubMetrics;
import com.hmdp.service.IAiCustomerService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.function.Consumer;

@Service
public class AiCustomerServiceImpl implements IAiCustomerService {
    @Resource private AiAgentWorkflow workflow;
    @Resource private AiAuditLogger auditLogger;
    @Resource private LocalHubMetrics metrics;

    @Override public String chat(String message) { return chat(message, null); }

    @Override
    public String chat(String message, String conversationId) {
        long started = System.nanoTime();
        try { return workflow.execute(message, conversationId); }
        finally { metrics.record("ai.chat", started); }
    }

    @Override
    public void stream(String message, Consumer<String> onToken, Runnable onComplete, Consumer<Throwable> onError) {
        stream(message, null, onToken, onComplete, onError);
    }

    @Override
    public void stream(String message, String conversationId, Consumer<String> onToken,
                       Runnable onComplete, Consumer<Throwable> onError) {
        try {
            String answer = chat(message, conversationId);
            for (int start = 0; start < answer.length(); start += 24) {
                onToken.accept(answer.substring(start, Math.min(answer.length(), start + 24)));
            }
            onComplete.run();
        } catch (Throwable error) {
            auditLogger.record("chat", "failed");
            onError.accept(error);
        }
    }
}
