package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Slf4j
@Component
public class LangChain4jChatClient {

    @Value("${localhub.ai.enabled:false}")
    private Boolean enabled;

    @Value("${localhub.ai.api-key:}")
    private String apiKey;

    @Value("${localhub.ai.model:gpt-4o-mini}")
    private String model;

    @Value("${localhub.ai.base-url:}")
    private String baseUrl;

    public boolean available() {
        return Boolean.TRUE.equals(enabled) && StrUtil.isNotBlank(apiKey);
    }

    public String chat(String prompt) {
        if (!available()) {
            return null;
        }
        try {
            Class<?> modelClass = Class.forName("dev.langchain4j.model.openai.OpenAiChatModel");
            Object builder = modelClass.getMethod("builder").invoke(null);
            invokeIfExists(builder, "apiKey", apiKey);
            invokeIfExists(builder, "modelName", model);
            invokeIfExists(builder, "temperature", 0.2);
            if (StrUtil.isNotBlank(baseUrl)) {
                invokeIfExists(builder, "baseUrl", baseUrl);
            }
            Object chatModel = builder.getClass().getMethod("build").invoke(builder);
            Method generate = chatModel.getClass().getMethod("generate", String.class);
            return String.valueOf(generate.invoke(chatModel, prompt));
        } catch (Exception e) {
            log.warn("LangChain4j chat failed, fallback to local RAG. {}", e.getMessage());
            return null;
        }
    }

    private void invokeIfExists(Object target, String methodName, Object value) {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterTypes().length == 1) {
                try {
                    method.invoke(target, value);
                } catch (Exception ignored) {
                    // keep compatibility with different LangChain4j versions
                }
                return;
            }
        }
    }
}
