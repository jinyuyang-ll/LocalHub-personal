package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hmdp.service.IAiCustomerService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AiCustomerServiceImpl implements IAiCustomerService {

    @Resource
    private AiToolGuard aiToolGuard;

    @Resource
    private RagSearchService ragSearchService;

    @Resource
    private LangChain4jChatClient langChain4jChatClient;

    @Override
    public String chat(String message) {
        if (StrUtil.isBlank(message)) {
            return "请告诉我你想查询店铺、优惠券、订单还是预约。";
        }

        String toolResponse = tryToolGuardedAnswer(message);
        List<RagSearchService.RagDocument> references = ragSearchService.search(message, 3);
        String context = references.stream()
                .map(document -> "【" + document.getTitle() + "】\n" + document.getContent())
                .collect(Collectors.joining("\n\n"));

        String prompt = buildPrompt(message, context, toolResponse);
        String llmAnswer = langChain4jChatClient.chat(prompt);
        if (StrUtil.isNotBlank(llmAnswer)) {
            return llmAnswer + buildReferenceText(references);
        }

        if (StrUtil.isNotBlank(toolResponse)) {
            return toolResponse + buildReferenceText(references);
        }
        if (!references.isEmpty()) {
            return "我根据知识库找到这些信息：\n\n" + context + buildReferenceText(references);
        }
        return "我是 LocalHub 客服助手，可以帮你处理店铺、优惠券、秒杀订单和预约问题。";
    }

    private String tryToolGuardedAnswer(String message) {
        String lower = message.toLowerCase();
        if (containsAny(lower, "订单", "order")) {
            return guarded("queryOrderStatus", "你可以在“我的订单”中查看状态，也可以提供订单号让我帮你查询。");
        }
        if (containsAny(lower, "预约", "reservation")) {
            return guarded("createReservation", "你可以选择店铺和时间创建预约；如果要取消，请提供预约编号。");
        }
        if (containsAny(lower, "优惠券", "券", "voucher")) {
            return guarded("queryVoucher", "普通券可在店铺详情页领取；秒杀券需要在活动时间内抢购。");
        }
        if (containsAny(lower, "店铺", "shop")) {
            return guarded("queryShop", "你可以按名称搜索店铺，也可以开启定位查看附近店铺。");
        }
        return null;
    }

    private String buildPrompt(String message, String context, String toolResponse) {
        return "你是 LocalHub 智慧生活平台客服助手。请只基于给定知识库和工具结果回答，回答要简洁、可执行。\n"
                + "如果知识库没有答案，请说明暂不确定并建议联系人工客服。\n\n"
                + "用户问题：\n" + message + "\n\n"
                + "工具结果：\n" + (toolResponse == null ? "无" : toolResponse) + "\n\n"
                + "知识库：\n" + (StrUtil.isBlank(context) ? "无" : context);
    }

    private String buildReferenceText(List<RagSearchService.RagDocument> references) {
        if (references == null || references.isEmpty()) {
            return "";
        }
        String titles = references.stream()
                .map(RagSearchService.RagDocument::getTitle)
                .distinct()
                .collect(Collectors.joining("、"));
        return "\n\n参考：" + titles;
    }

    private String guarded(String toolName, String response) {
        if (!aiToolGuard.isAllowed(toolName)) {
            return "该工具暂不可用，请联系人工客服。";
        }
        return response;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
