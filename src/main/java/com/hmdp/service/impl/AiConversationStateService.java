package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
    private String key(String id) { return "ai:conversation:" + id.replaceAll("[^a-zA-Z0-9._:-]", "_"); }
}
