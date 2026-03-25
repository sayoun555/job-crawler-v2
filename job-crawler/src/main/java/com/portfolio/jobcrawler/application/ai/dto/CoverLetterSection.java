package com.portfolio.jobcrawler.application.ai.dto;

/**
 * 커스텀 자소서 문항 DTO.
 * @param title 문항 제목 (예: "지원동기")
 * @param rule 문항 규칙 (예: "회사 사업과 내 경험 접점, 500자")
 * @param content AI 생성된 내용 (생성 후 채워짐)
 */
public record CoverLetterSection(String title, String rule, String content) {

    /** 요청용 (content 없이) */
    public CoverLetterSection(String title, String rule) {
        this(title, rule, null);
    }

    /** content를 채운 새 인스턴스 반환 */
    public CoverLetterSection withContent(String content) {
        return new CoverLetterSection(this.title, this.rule, content);
    }
}
