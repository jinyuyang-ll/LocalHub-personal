package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hmdp.service.IAiCustomerService;
import com.hmdp.config.LocalHubMetrics;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class AiCustomerServiceImpl implements IAiCustomerService {

    @Resource
    private AiToolGuard aiToolGuard;

    @Resource
    private RagSearchService ragSearchService;

    @Resource
    private LangChain4jChatClient langChain4jChatClient;
    @Resource
    private AiBusinessTools aiBusinessTools;
    @Resource
    private LocalHubMetrics metrics;

    private static final Pattern ORDER_ID = Pattern.compile("(?:订单|order)\\s*[号#:]?\\s*(\\d{6,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHOP_ID = Pattern.compile("(?:店铺|shop)\\s*[号#:]?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    @Override
    public String chat(String message) {
        long started = System.nanoTime();
        try {
            return doChat(message);
        } finally {
            metrics.record("ai.chat", started);
        }
    }

    private String doChat(String message) {
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

    @Override
    public void stream(String message, Consumer<String> onToken, Runnable onComplete, Consumer<Throwable> onError) {
        long started = System.nanoTime();
        try {
            String toolResponse = tryToolGuardedAnswer(message);
            List<RagSearchService.RagDocument> references = ragSearchService.search(message, 3);
            String context = references.stream()
                    .map(document -> "【" + document.getTitle() + "】\n" + document.getContent())
                    .collect(Collectors.joining("\n\n"));
            String prompt = buildPrompt(message, context, toolResponse);
            boolean startedStreaming = langChain4jChatClient.stream(prompt, onToken, () -> {
                onToken.accept(buildReferenceText(references));
                metrics.record("ai.chat", started);
                onComplete.run();
            }, onError);
            if (startedStreaming) return;
            String fallback = StrUtil.isNotBlank(toolResponse) ? toolResponse + buildReferenceText(references)
                    : !references.isEmpty() ? "我根据知识库找到这些信息：\n\n" + context + buildReferenceText(references)
                    : "我是 LocalHub 客服助手，可以帮你处理店铺、优惠券、秒杀订单和预约问题。";
            for (int start = 0; start < fallback.length(); start += 24) {
                onToken.accept(fallback.substring(start, Math.min(fallback.length(), start + 24)));
            }
            metrics.record("ai.chat", started);
            onComplete.run();
        } catch (Throwable e) {
            onError.accept(e);
        }
    }

    private String tryToolGuardedAnswer(String message) {
        String lower = message.toLowerCase();
        if (containsAny(lower, "订单", "order")) {
            Matcher matcher = ORDER_ID.matcher(message);
            if (matcher.find()) {
                try {
                    return aiBusinessTools.queryOrderStatus(Long.valueOf(matcher.group(1)));
                } catch (RuntimeException e) {
                    return e.getMessage();
                }
            }
            return guarded("queryOrderStatus", "请提供订单号，例如：查询订单 123456789。查询只会返回当前登录用户的订单。 ");
        }
        if (containsAny(lower, "预约", "reservation")) {
            return guarded("createReservation", "你可以选择店铺和时间创建预约；如果要取消，请提供预约编号。");
        }
        if (containsAny(lower, "优惠券", "券", "voucher")) {
            return guarded("queryVoucher", "普通券可在店铺详情页领取；秒杀券需要在活动时间内抢购。");
        }
        if (containsAny(lower, "店铺", "shop")) {
            Matcher matcher = SHOP_ID.matcher(message);
            if (matcher.find()) {
                metrics.increment("ai.tool", "query_shop");
                return aiBusinessTools.queryShop(Long.valueOf(matcher.group(1)));
            }
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
