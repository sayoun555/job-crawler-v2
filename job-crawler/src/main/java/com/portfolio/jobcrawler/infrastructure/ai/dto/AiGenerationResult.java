package com.portfolio.jobcrawler.infrastructure.ai.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AiGenerationResult {
    private String generatedText;
    private boolean success;
    private String errorMessage;

    public static AiGenerationResult success(String text) {
        return AiGenerationResult.builder().generatedText(text).success(true).build();
    }

    public static AiGenerationResult failure(String error) {
        return AiGenerationResult.builder().success(false).errorMessage(error).build();
    }
}
