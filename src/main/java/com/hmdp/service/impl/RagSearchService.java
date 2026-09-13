package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RagSearchService {

    private final List<RagDocument> documents = new ArrayList<>();

    @Resource private EmbeddingService embeddingService;
    @Resource private LangChain4jChatClient chatClient;

    @Value("${localhub.ai.retrieval.min-score:0.25}") private double minScore;
    @Value("${localhub.ai.retrieval.candidate-limit:12}") private int candidateLimit;
    @Value("${localhub.ai.retrieval.vector-weight:0.65}") private double vectorWeight;

    private volatile boolean embeddingsInitialized;

    @PostConstruct
    public void init() {
        loadMarkdown("rag/localhub-faq.md");
        log.info("RAG documents loaded, chunks={}", documents.size());
    }

    public List<RagDocument> search(String query, int limit) {
        if (StrUtil.isBlank(query) || documents.isEmpty()) return new ArrayList<>();
        ensureDocumentEmbeddings();
        String rewritten = rewriteQuery(query);
        Set<String> queryTokens = tokenize(rewritten);
        double[] queryEmbedding = embeddingService.embed(rewritten);

        List<RagDocument> candidates = documents.stream()
                .map(document -> scoreCandidate(document, queryTokens, queryEmbedding))
                .sorted(Comparator.comparingDouble(RagDocument::getCandidateScore).reversed())
                .limit(Math.max(limit, candidateLimit))
                .collect(Collectors.toList());

        return candidates.stream()
                .map(document -> rerank(document, queryTokens))
                .filter(document -> document.getRelevance() >= minScore)
                .sorted(Comparator.comparingDouble(RagDocument::getRelevance).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private String rewriteQuery(String query) {
        String normalized = normalize(query);
        String rewritten = chatClient.chat("将用户问题改写成一句适合知识库检索的查询，保留实体、编号和约束，只输出改写结果：" + normalized);
        if (StrUtil.isBlank(rewritten) || rewritten.length() > 300) {
            return normalized.replace("怎么", "如何").replace("咋", "如何")
                    .replace("优惠", "优惠券").replace("秒杀", "抢购 秒杀");
        }
        return normalize(rewritten);
    }

    private RagDocument scoreCandidate(RagDocument source, Set<String> queryTokens, double[] queryEmbedding) {
        RagDocument result = source.copy();
        double lexical = overlap(queryTokens, source.getTokens());
        double vector = embeddingService.cosine(queryEmbedding, source.getEmbedding());
        double semantic = Math.max(0, vector);
        double effectiveVectorWeight = queryEmbedding == null || source.getEmbedding() == null ? 0 : vectorWeight;
        result.setLexicalScore(lexical);
        result.setVectorScore(semantic);
        result.setCandidateScore(lexical * (1 - effectiveVectorWeight) + semantic * effectiveVectorWeight);
        return result;
    }

    private RagDocument rerank(RagDocument document, Set<String> queryTokens) {
        double titleScore = overlap(queryTokens, tokenize(document.getTitle()));
        document.setRelevance(document.getCandidateScore() * 0.85 + titleScore * 0.15);
        return document;
    }

    private double overlap(Set<String> query, Set<String> content) {
        if (query.isEmpty() || content == null || content.isEmpty()) return 0;
        int matches = 0;
        for (String token : query) if (content.contains(token)) matches++;
        return (double) matches / query.size();
    }

    private Set<String> tokenize(String text) {
        String normalized = normalize(text);
        Set<String> tokens = new HashSet<>();
        for (String word : normalized.split("\\s+")) {
            if (word.isEmpty()) continue;
            tokens.add(word);
            if (containsCjk(word) && word.length() > 1) {
                for (int i = 0; i < word.length() - 1; i++) tokens.add(word.substring(i, i + 2));
            }
        }
        return tokens;
    }

    private boolean containsCjk(String value) {
        for (int i = 0; i < value.length(); i++) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(value.charAt(i));
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) return true;
        }
        return false;
    }

    private synchronized void ensureDocumentEmbeddings() {
        if (embeddingsInitialized || !embeddingService.available()) return;
        List<double[]> vectors = embeddingService.embedAll(
                documents.stream().map(RagDocument::getContent).collect(Collectors.toList()));
        if (vectors.size() == documents.size()) {
            for (int i = 0; i < documents.size(); i++) documents.get(i).setEmbedding(vectors.get(i));
            embeddingsInitialized = true;
            log.info("RAG embeddings initialized, model-backed chunks={}", vectors.size());
        }
    }

    private void loadMarkdown(String path) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder chunk = new StringBuilder();
            String title = path;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("## ")) {
                    addChunk(title, chunk);
                    title = line.substring(3).trim();
                    chunk = new StringBuilder();
                } else if (line.trim().isEmpty()) {
                    addChunk(title, chunk);
                    chunk = new StringBuilder();
                } else {
                    chunk.append(line).append('\n');
                }
            }
            addChunk(title, chunk);
        } catch (Exception e) {
            log.warn("Failed to load RAG document: {}", path, e);
        }
    }

    private void addChunk(String title, StringBuilder chunk) {
        String content = chunk.toString().trim();
        if (content.isEmpty() || content.startsWith("# ")) return;
        RagDocument document = new RagDocument();
        document.setTitle(title);
        document.setContent(content);
        document.setTokens(tokenize(title + " " + content));
        documents.add(document);
    }

    private String normalize(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[，。！？、；：,.!?;:/\\\\()（）{}\\[\\]\"']", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    @Data
    public static class RagDocument {
        private String title;
        private String content;
        private Set<String> tokens;
        private double[] embedding;
        private double lexicalScore;
        private double vectorScore;
        private double candidateScore;
        private double relevance;

        private RagDocument copy() {
            RagDocument copy = new RagDocument();
            copy.title = title;
            copy.content = content;
            copy.tokens = tokens;
            copy.embedding = embedding;
            return copy;
        }
    }
}
