package com.hmdp.service.impl;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RagSearchService {

    private final List<RagDocument> documents = new ArrayList<>();

    @PostConstruct
    public void init() {
        loadMarkdown("rag/localhub-faq.md");
        log.info("RAG documents loaded, chunks={}", documents.size());
    }

    public List<RagDocument> search(String query, int limit) {
        String normalizedQuery = normalize(query);
        return documents.stream()
                .map(document -> document.withScore(score(document.getContent(), normalizedQuery)))
                .filter(document -> document.getScore() > 0)
                .sorted(Comparator.comparingInt(RagDocument::getScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private void loadMarkdown(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder chunk = new StringBuilder();
            String title = path;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("## ")) {
                    addChunk(title, chunk);
                    title = line.substring(3).trim();
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
        if (content.length() == 0 || content.startsWith("# ")) {
            return;
        }
        RagDocument document = new RagDocument();
        document.setTitle(title);
        document.setContent(content);
        document.setScore(0);
        documents.add(document);
    }

    private int score(String content, String query) {
        String normalizedContent = normalize(content);
        int score = 0;
        for (String token : query.split("\\s+")) {
            if (token.length() > 0 && normalizedContent.contains(token)) {
                score += token.length();
            }
        }
        return score;
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[，。！？、；：,.!?;:/\\\\()（）{}\\[\\]\"']", " ");
    }

    @Data
    public static class RagDocument {
        private String title;
        private String content;
        private Integer score;

        public RagDocument withScore(Integer score) {
            RagDocument document = new RagDocument();
            document.setTitle(title);
            document.setContent(content);
            document.setScore(score);
            return document;
        }
    }
}
