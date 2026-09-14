package com.hmdp.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagEvaluationTest {

    @Test void offlineHybridRetrievalHitAt3IsMeasured() throws Exception {
        EmbeddingService embedding = mock(EmbeddingService.class);
        LangChain4jChatClient chat = mock(LangChain4jChatClient.class);
        when(embedding.available()).thenReturn(false);
        when(chat.chat(anyString())).thenReturn("");
        RagSearchService service = new RagSearchService();
        ReflectionTestUtils.setField(service, "embeddingService", embedding);
        ReflectionTestUtils.setField(service, "chatClient", chat);
        ReflectionTestUtils.setField(service, "minScore", 0.01d);
        ReflectionTestUtils.setField(service, "candidateLimit", 12);
        ReflectionTestUtils.setField(service, "vectorWeight", 0.65d);
        service.init();

        int total = 0;
        int hits = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("evals/rag-eval.jsonl").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                JSONObject sample = JSONUtil.parseObj(line);
                List<RagSearchService.RagDocument> result = service.search(sample.getStr("query"), 3);
                if (result.stream().anyMatch(document -> sample.getStr("expectedTitle").equals(document.getTitle()))) hits++;
                total++;
            }
        }
        double hitAt3 = total == 0 ? 0 : (double) hits / total;
        System.out.printf("RAG eval: total=%d hits=%d Hit@3=%.4f%n", total, hits, hitAt3);
        assertTrue(total >= 50, "evaluation set must contain at least 50 questions");
        assertTrue(hitAt3 >= 0.80, "offline lexical fallback Hit@3 regression: " + hitAt3);
    }
}
