package com.portfolio.jobcrawler.infrastructure.ai.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AiGenerationRequest {
    private String userProfile; // 사용자 프로필 (학력, 경력, 기술스택, 강점)
    private String jobDescription; // 공고 상세 내용
    private String companyInfo; // 기업 정보
    private String matchedProjects; // 매칭된 프로젝트들 (JSON)
    private String templateContent; // 템플릿 내용 (플레이스홀더 포함)
    private GenerationType type; // 생성 타입

    public enum GenerationType {
        COVER_LETTER,
        PORTFOLIO,
        COMPANY_ANALYSIS,
        PROJECT_SUMMARY
    }
}
