package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class LangChain4jChatClient {

    @Value("${localhub.ai.enabled:false}") private boolean enabled;
    @Value("${localhub.ai.api-key:}") private String apiKey;
    @Value("${localhub.ai.model:qwen-plus}") private String modelName;
    @Value("${localhub.ai.base-url:}") private String baseUrl;
    @Value("${localhub.ai.timeout-seconds:25}") private int timeoutSeconds;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile long circuitOpenUntil;
    private volatile ChatLanguageModel model;

    public boolean available() {
        return enabled && StrUtil.isNotBlank(apiKey) && StrUtil.isNotBlank(baseUrl);
    }

    public String chat(String prompt) {
        if (!canCall()) return null;
        try {
            String response = model().generate(prompt);
            consecutiveFailures.set(0);
            return response;
        } catch (RuntimeException e) {
            recordFailure(e);
            return null;
        }
    }

    public String chatWithTools(String prompt, Object tools) {
        if (!canCall()) return null;
        try {
            NativeAiAssistant assistant = AiServices.builder(NativeAiAssistant.class)
                    .chatLanguageModel(model())
                    .tools(tools)
                    .build();
            String response = assistant.answer(prompt);
            consecutiveFailures.set(0);
            return response;
        } catch (RuntimeException e) {
            recordFailure(e);
            return null;
        }
    }

    private boolean canCall() {
        return available() && System.currentTimeMillis() >= circuitOpenUntil;
    }

    private ChatLanguageModel model() {
        if (model == null) {
            synchronized (this) {
                if (model == null) {
                    model = OpenAiChatModel.builder()
                            .apiKey(apiKey)
                            .baseUrl(baseUrl)
                            .modelName(modelName)
                            .temperature(0.2)
                            .timeout(Duration.ofSeconds(timeoutSeconds))
                            .maxRetries(2)
                            .build();
                }
            }
        }
        return model;
    }

    private void recordFailure(RuntimeException error) {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= 3) circuitOpenUntil = System.currentTimeMillis() + 30_000L;
        log.warn("Alibaba Bailian model call failed, failures={}: {}", failures, error.getMessage());
    }
}
