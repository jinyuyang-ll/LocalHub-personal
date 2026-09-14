package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import com.hmdp.utils.UserHolder;

import static com.hmdp.utils.RedisConstants.AI_CONVERSATION_KEY;

@Component
public class AiConversationStateService {
    @Resource(name = "stringRedisTemplate") private StringRedisTemplate redis;
    @Value("${localhub.ai.session-ttl-seconds:1800}") private long ttlSeconds;

    public Map<String, Object> load(String conversationId) {
        Map<String, Object> state = new LinkedHashMap<>();
        if (conversationId == null || conversationId.trim().isEmpty()) return state;
        String raw = redis.opsForValue().get(key(conversationId));
        if (raw != null) state = JSONUtil.toBean(raw, Map.class);
        return state == null ? new LinkedHashMap<>() : state;
    }

    public void save(String conversationId, Map<String, Object> state) {
        if (conversationId == null || conversationId.trim().isEmpty()) return;
        redis.opsForValue().set(key(conversationId), JSONUtil.toJsonStr(state), ttlSeconds, TimeUnit.SECONDS);
    }

    public void clear(String conversationId) { if (conversationId != null) redis.delete(key(conversationId)); }

    @SuppressWarnings("unchecked")
    public void appendTurn(String conversationId, Map<String, Object> state, String user, String assistant) {
        if (conversationId == null || conversationId.trim().isEmpty()) return;
        List<Map<String, String>> history = state.get("history") instanceof List
                ? new ArrayList<>((List<Map<String, String>>) state.get("history")) : new ArrayList<>();
        Map<String, String> userTurn = new LinkedHashMap<>();
        userTurn.put("role", "user"); userTurn.put("content", trim(user));
        Map<String, String> assistantTurn = new LinkedHashMap<>();
        assistantTurn.put("role", "assistant"); assistantTurn.put("content", trim(assistant));
        history.add(userTurn); history.add(assistantTurn);
        while (history.size() > 10) history.remove(0);
        state.put("history", history);
        save(conversationId, state);
    }

    private String trim(String text) { return text != null && text.length() > 2000 ? text.substring(0, 2000) : text; }
    private String key(String id) {
        String owner = UserHolder.getUser() == null ? "anonymous" : String.valueOf(UserHolder.getUser().getId());
        return AI_CONVERSATION_KEY + owner + ":" + id.replaceAll("[^a-zA-Z0-9._:-]", "_");
    }
}
