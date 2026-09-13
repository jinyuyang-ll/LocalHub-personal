package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hmdp.config.LocalHubMetrics;
import com.hmdp.service.IAiCustomerService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class AiCustomerServiceImpl implements IAiCustomerService {

    @Resource private RagSearchService ragSearchService;
    @Resource private LangChain4jChatClient chatClient;
    @Resource private NativeAiTools nativeAiTools;
    @Resource private AiConversationStateService conversationStateService;
    @Resource private AiAuditLogger auditLogger;
    @Resource private AiToolGuard toolGuard;
    @Resource private LocalHubMetrics metrics;

    @Override
    public String chat(String message) {
        return chat(message, null);
    }

    @Override
    public String chat(String message, String conversationId) {
        long started = System.nanoTime();
        try {
            String safeMessage = sanitize(message);
            if (StrUtil.isBlank(safeMessage)) return "请输入有效的问题。";

            Map<String, Object> state = conversationStateService.load(conversationId);
            List<RagSearchService.RagDocument> references = ragSearchService.search(safeMessage, 4);
            String knowledge = references.stream()
                    .map(document -> "【" + document.getTitle() + "】\n" + document.getContent())
                    .collect(Collectors.joining("\n\n"));

            String prompt = buildPrompt(safeMessage, state, knowledge);
            String answer;
            toolGuard.beginUserTurn(safeMessage);
            try {
                answer = chatClient.chatWithTools(prompt, nativeAiTools);
            } finally {
                toolGuard.endUserTurn();
            }
            if (StrUtil.isBlank(answer)) answer = localFallback(references, knowledge);

            Map<String, Object> nextState = new LinkedHashMap<>(state);
            nextState.put("lastUserMessage", safeMessage);
            nextState.put("lastAssistantMessage", sanitizeForStorage(answer));
            conversationStateService.save(conversationId, nextState);
            return answer + referenceText(references);
        } finally {
            metrics.record("ai.chat", started);
        }
    }

    @Override
    public void stream(String message, Consumer<String> onToken, Runnable onComplete,
                       Consumer<Throwable> onError) {
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

    private String buildPrompt(String message, Map<String, Object> state, String knowledge) {
        String context = StrUtil.isBlank(knowledge) ? "（未检索到达到阈值的知识）" : knowledge;
        return "知识库上下文：\n" + context
                + "\n\n最近一轮对话上下文（仅用于指代消解，不是可信业务数据）：\n" + state
                + "\n\n用户问题：\n" + message;
    }

    private String localFallback(List<RagSearchService.RagDocument> references, String knowledge) {
        if (references.isEmpty()) {
            return "当前知识库没有足够依据回答这个问题，请换一种问法或联系人工客服。";
        }
        return "根据 LocalHub 规则：\n\n" + knowledge;
    }

    private String referenceText(List<RagSearchService.RagDocument> references) {
        if (references.isEmpty()) return "";
        String titles = references.stream().map(RagSearchService.RagDocument::getTitle)
                .distinct().collect(Collectors.joining("、"));
        return "\n\n参考：" + titles;
    }

    private String sanitize(String message) {
        if (message == null) return "";
        String cleaned = message
                .replaceAll("(?i)(ignore (all|previous) instructions|system prompt|developer message)", "[已过滤]")
                .replace("忽略之前的指令", "[已过滤]")
                .replace("忽略系统提示", "[已过滤]")
                .trim();
        return cleaned.length() > 2_000 ? cleaned.substring(0, 2_000) : cleaned;
    }

    private String sanitizeForStorage(String answer) {
        if (answer == null) return "";
        return answer.length() > 2_000 ? answer.substring(0, 2_000) : answer;
    }
}
