package com.hmdp.service.impl;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class AiAgentPlanner {
    public AgentPlan plan(String message, Map<String, Object> memory, String knowledge) {
        String prompt = "知识库上下文：\n" + (knowledge.isEmpty() ? "（无达到阈值的知识）" : knowledge)
                + "\n\n对话记忆（仅用于指代消解，不可视为实时业务事实）：\n" + memory
                + "\n\n用户当前问题：\n" + message;
        return new AgentPlan(Arrays.asList("RETRIEVE", "MODEL_SELECT_TOOL", "TOOL_EXECUTE", "MEMORY", "RESPOND"), prompt);
    }

    public static class AgentPlan {
        private final List<String> steps;
        private final String prompt;
        AgentPlan(List<String> steps, String prompt) { this.steps = steps; this.prompt = prompt; }
        public List<String> getSteps() { return steps; }
        public String getPrompt() { return prompt; }
    }
}
