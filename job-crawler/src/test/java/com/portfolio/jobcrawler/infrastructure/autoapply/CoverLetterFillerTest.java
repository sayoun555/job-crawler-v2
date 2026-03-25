package com.portfolio.jobcrawler.infrastructure.autoapply;

import com.portfolio.jobcrawler.application.ai.dto.CoverLetterSection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CoverLetterFillerTest {

    @Test
    @DisplayName("정상 JSON을 CoverLetterSection 리스트로 파싱한다")
    void parseSections_validJson() {
        String json = """
                [
                  {"title":"지원동기","rule":"500자 이내","content":"저는 이 회사에..."},
                  {"title":"직무역량","rule":"1000자","content":"Java/Spring Boot 경험..."},
                  {"title":"입사 후 포부","rule":"300자","content":"입사 후에는..."}
                ]
                """;

        List<CoverLetterSection> sections = CoverLetterFiller.parseSections(json);

        assertThat(sections).hasSize(3);
        assertThat(sections.get(0).title()).isEqualTo("지원동기");
        assertThat(sections.get(0).content()).isEqualTo("저는 이 회사에...");
        assertThat(sections.get(1).title()).isEqualTo("직무역량");
        assertThat(sections.get(2).title()).isEqualTo("입사 후 포부");
    }

    @Test
    @DisplayName("빈 JSON 배열은 빈 리스트를 반환한다")
    void parseSections_emptyArray() {
        List<CoverLetterSection> sections = CoverLetterFiller.parseSections("[]");
        assertThat(sections).isEmpty();
    }

    @Test
    @DisplayName("잘못된 JSON은 빈 리스트를 반환한다")
    void parseSections_invalidJson() {
        List<CoverLetterSection> sections = CoverLetterFiller.parseSections("invalid json");
        assertThat(sections).isEmpty();
    }

    @Test
    @DisplayName("null JSON은 빈 리스트를 반환한다")
    void parseSections_nullJson() {
        List<CoverLetterSection> sections = CoverLetterFiller.parseSections(null);
        assertThat(sections).isEmpty();
    }
}
