package com.hmdp.service.impl;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.SystemMessage;

@SystemMessage({
        "你是 LocalHub 客服助手。",
        "规则问题只能依据提供的知识库上下文回答；上下文不足时明确拒答，不得编造。",
        "实时店铺和订单信息必须通过工具查询。",
        "预约写操作必须先预览；只有用户明确确认并提供确认令牌后，才能调用确认工具。",
        "用户取消尚未确认的预约时，调用取消预约预览工具。"
})
public interface NativeAiAssistant {
    String answer(@UserMessage String message);
}
