package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EmbeddingService {

    @Value("${localhub.ai.enabled:false}") private boolean aiEnabled;
    @Value("${localhub.ai.api-key:}") private String apiKey;
    @Value("${localhub.ai.embedding.base-url:${localhub.ai.base-url:}}") private String baseUrl;
    @Value("${localhub.ai.embedding.model:text-embedding-v3}") private String modelName;
    @Value("${localhub.ai.embedding.dimensions:1024}") private int dimensions;

    private volatile EmbeddingModel model;

    public boolean available() {
        return aiEnabled && StrUtil.isNotBlank(apiKey) && StrUtil.isNotBlank(baseUrl);
    }

    public double[] embed(String text) {
        List<double[]> vectors = embedAll(Collections.singletonList(text));
        return vectors.isEmpty() ? null : vectors.get(0);
    }

    public List<double[]> embedAll(List<String> texts) {
        if (!available() || texts == null || texts.isEmpty()) return Collections.emptyList();
        try {
            List<TextSegment> segments = texts.stream().map(TextSegment::from).collect(Collectors.toList());
            List<Embedding> embeddings = model().embedAll(segments).content();
            List<double[]> result = new ArrayList<>(embeddings.size());
            for (Embedding embedding : embeddings) {
                float[] source = embedding.vector();
                double[] target = new double[source.length];
                for (int i = 0; i < source.length; i++) target[i] = source[i];
                result.add(target);
            }
            return result;
        } catch (RuntimeException e) {
            log.warn("Alibaba Bailian embedding request failed; lexical retrieval remains available. {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public double cosine(double[] left, double[] right) {
        if (left == null || right == null || left.length == 0 || right.length == 0) return 0;
        double dot = 0, leftNorm = 0, rightNorm = 0;
        int length = Math.min(left.length, right.length);
        for (int i = 0; i < length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        return leftNorm == 0 || rightNorm == 0 ? 0 : dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private EmbeddingModel model() {
        if (model == null) {
            synchronized (this) {
                if (model == null) {
                    model = OpenAiEmbeddingModel.builder()
                            .apiKey(apiKey)
                            .baseUrl(baseUrl)
                            .modelName(modelName)
                            .dimensions(dimensions)
                            .timeout(Duration.ofSeconds(20))
                            .maxRetries(2)
                            .build();
                }
            }
        }
        return model;
    }
}
