package com.portfolio.jobcrawler.infrastructure.ai.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AiGenerationRequest {
    private String userProfile;
    private String jobDescription;
    private String companyInfo;
    private String matchedProjects;
    private String templateContent;
    private String sourceSite;
    private GenerationType type;
    private String customSections;
    private String additionalRequest;
    private List<String> imageUrls; // 멀티모달 이미지 URL (노션 등)

    public enum GenerationType {
        COVER_LETTER,
        CUSTOM_COVER_LETTER,
        PORTFOLIO,
        CUSTOM_PORTFOLIO,
        COMPANY_ANALYSIS,
        PROJECT_SUMMARY,
        COVER_LETTER_ANALYSIS,
        COVER_LETTER_PRESET_SEARCH
    }
}
