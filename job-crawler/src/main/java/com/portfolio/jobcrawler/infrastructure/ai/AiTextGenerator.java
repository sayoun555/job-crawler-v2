package com.portfolio.jobcrawler.infrastructure.ai;

import com.portfolio.jobcrawler.infrastructure.ai.dto.AiGenerationRequest;
import com.portfolio.jobcrawler.infrastructure.ai.dto.AiGenerationResult;

/**
 * AI 텍스트 생성 인터페이스.
 * OpenClaw 등 다양한 AI 백엔드로 교체 가능 (Strategy Pattern).
 */
public interface AiTextGenerator {

    /** AI 텍스트 생성 */
    AiGenerationResult generate(AiGenerationRequest request);

    /** 적합률(0~100) 산출 */
    int calculateMatchScore(String userProfile, String jobDescription);

    /** 적합률 + 근거 산출 */
    java.util.Map<String, Object> calculateMatchScoreWithReason(String userProfile, String jobDescription);

    /** 적합률(0~100) 산출 - 이미지 포함 */
    int calculateMatchScore(String userProfile, String jobDescription, java.util.List<String> imageUrls);

    /** 프로젝트 자동 정리 (GitHub README 기반) */
    String summarizeProject(String projectDescription, String techStack);
}
