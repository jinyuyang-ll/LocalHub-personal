package com.hmdp.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiChatResponse {

    private String answer;

    private List<String> references;

    private Boolean llmUsed;
}
