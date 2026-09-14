package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AiAgentWorkflow {
    @Resource private RagSearchService ragSearchService;
    @Resource private AiAgentPlanner planner;
    @Resource private LangChain4jChatClient chatClient;
    @Resource private NativeAiTools toolExecutor;
    @Resource private AiConversationStateService memory;
    @Resource private AiToolGuard toolGuard;
    @Resource private AiGuardrailService guardrail;
    @Resource private AiAuditLogger auditLogger;

    public String execute(String rawMessage, String conversationId) {
        String message = guardrail.sanitizeInput(rawMessage);
        Map<String, Object> state = memory.load(conversationId);
        List<RagSearchService.RagDocument> references = ragSearchService.search(message, 4);
        String knowledge = references.stream()
                .map(document -> "【" + document.getTitle() + "】\n" + document.getContent())
                .collect(Collectors.joining("\n\n"));
        AiAgentPlanner.AgentPlan plan = planner.plan(message, state, knowledge);
        auditLogger.record("plan", String.join("->", plan.getSteps()));
        String answer;
        toolGuard.beginUserTurn(message);
        try {
            answer = chatClient.chatWithTools(plan.getPrompt(), toolExecutor);
        } finally {
            toolGuard.endUserTurn();
        }
        if (StrUtil.isBlank(answer)) {
            answer = references.isEmpty()
                    ? "当前知识库没有足够依据回答这个问题，请换一种问法或联系人工客服。"
                    : "根据 LocalHub 规则：\n\n" + knowledge;
        }
        answer = guardrail.sanitizeOutput(answer);
        memory.appendTurn(conversationId, state, message, answer);
        if (!references.isEmpty()) {
            answer += "\n\n参考：" + references.stream().map(RagSearchService.RagDocument::getTitle)
                    .distinct().collect(Collectors.joining("、"));
        }
        return answer;
    }
}
