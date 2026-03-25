package com.portfolio.jobcrawler.infrastructure.autoapply;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.portfolio.jobcrawler.application.ai.dto.CoverLetterSection;
import com.portfolio.jobcrawler.domain.jobapply.entity.JobApplication;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 자소서 폼 채우기 유틸리티.
 * - Mode 1 (기본): 단일 textarea에 전체 자소서 입력
 * - Mode 2 (커스텀): 다중 textarea에 문항별 매핑
 */
@Slf4j
public final class CoverLetterFiller {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private CoverLetterFiller() {
    }

    /**
     * JobApplication의 자소서를 페이지의 textarea에 채운다.
     * coverLetterSections가 있으면 다중 매핑, 없으면 단일 입력.
     */
    public static void fill(Page page, JobApplication app) {
        if (app.hasCoverLetterSections()) {
            List<CoverLetterSection> sections = parseSections(app.getCoverLetterSections());
            if (!sections.isEmpty()) {
                fillMultipleSections(page, sections);
                return;
            }
        }
        fillSingleCoverLetter(page, app.getCoverLetter());
    }

    private static void fillMultipleSections(Page page, List<CoverLetterSection> sections) {
        Locator textareas = page.locator("textarea:visible");
        int textareaCount = textareas.count();

        if (textareaCount == 0) {
            fillContentEditable(page, combineSections(sections));
            return;
        }

        // textarea가 1개면 전체 내용을 합쳐서 입력
        if (textareaCount == 1) {
            textareas.first().fill(combineSections(sections));
            log.info("[CoverLetterFiller] textarea 1개 → 전체 섹션 합쳐서 입력");
            return;
        }

        // 다중 textarea → 라벨 매칭 시도
        boolean[] textareaFilled = new boolean[textareaCount];
        boolean[] sectionUsed = new boolean[sections.size()];

        matchByLabel(page, textareas, textareaCount, sections, textareaFilled, sectionUsed);
        fillRemainingSequentially(textareas, textareaCount, sections, textareaFilled, sectionUsed);
    }

    /**
     * textarea 근처의 라벨/제목 텍스트와 섹션 title을 매칭하여 채운다.
     */
    private static void matchByLabel(Page page, Locator textareas, int count,
                                     List<CoverLetterSection> sections,
                                     boolean[] textareaFilled, boolean[] sectionUsed) {
        for (int i = 0; i < count; i++) {
            String label = detectLabel(page, textareas.nth(i));
            if (label.isBlank()) continue;

            for (int j = 0; j < sections.size(); j++) {
                if (sectionUsed[j]) continue;

                if (titleMatchesLabel(sections.get(j).title(), label)) {
                    textareas.nth(i).fill(sections.get(j).content());
                    textareaFilled[i] = true;
                    sectionUsed[j] = true;
                    log.info("[CoverLetterFiller] 라벨 매칭 성공: '{}' → textarea[{}]",
                            sections.get(j).title(), i);
                    break;
                }
            }
        }
    }

    /**
     * 라벨 매칭되지 않은 나머지 textarea를 순서대로 채운다.
     */
    private static void fillRemainingSequentially(Locator textareas, int count,
                                                  List<CoverLetterSection> sections,
                                                  boolean[] textareaFilled, boolean[] sectionUsed) {
        int sectionIdx = 0;
        for (int i = 0; i < count; i++) {
            if (textareaFilled[i]) continue;

            while (sectionIdx < sections.size() && sectionUsed[sectionIdx]) {
                sectionIdx++;
            }
            if (sectionIdx >= sections.size()) break;

            textareas.nth(i).fill(sections.get(sectionIdx).content());
            sectionUsed[sectionIdx] = true;
            log.info("[CoverLetterFiller] 순차 매핑: '{}' → textarea[{}]",
                    sections.get(sectionIdx).title(), i);
            sectionIdx++;
        }
    }

    /**
     * textarea 근처의 라벨 텍스트를 감지한다.
     * 우선순위: 1) 연결된 label 태그 2) 바로 앞 형제 요소 3) name/placeholder 속성
     */
    private static String detectLabel(Page page, Locator textarea) {
        // 1. textarea의 id로 연결된 label
        String id = textarea.getAttribute("id");
        if (id != null && !id.isBlank()) {
            Locator label = page.locator("label[for='" + id + "']");
            if (label.count() > 0) {
                return label.first().textContent().trim();
            }
        }

        // 2. name/placeholder 속성
        String name = orEmpty(textarea.getAttribute("name"));
        String placeholder = orEmpty(textarea.getAttribute("placeholder"));
        String title = orEmpty(textarea.getAttribute("title"));
        String combined = name + " " + placeholder + " " + title;
        if (!combined.isBlank()) {
            return combined.trim();
        }

        // 3. 바로 앞 형제 요소의 텍스트 (JS로 탐색)
        try {
            Object prevText = textarea.evaluate(
                    "el => { " +
                    "  let prev = el.previousElementSibling; " +
                    "  if (prev) return prev.textContent.trim(); " +
                    "  let parent = el.parentElement; " +
                    "  if (parent) { " +
                    "    let heading = parent.querySelector('h1,h2,h3,h4,h5,h6,label,strong,b,p,span'); " +
                    "    if (heading && heading !== el) return heading.textContent.trim(); " +
                    "  } " +
                    "  return ''; " +
                    "}");
            if (prevText instanceof String) {
                return ((String) prevText).trim();
            }
        } catch (Exception e) {
            log.debug("[CoverLetterFiller] 라벨 감지 JS 실행 실패: {}", e.getMessage());
        }

        return "";
    }

    /**
     * 섹션 title과 라벨 텍스트가 매칭되는지 판단한다.
     * 핵심 키워드 기반 매칭 (완전 일치가 아닌 포함 관계).
     */
    private static boolean titleMatchesLabel(String sectionTitle, String labelText) {
        String normalizedTitle = normalize(sectionTitle);
        String normalizedLabel = normalize(labelText);

        // 직접 포함 (제목이 라벨에 포함되거나, 라벨이 제목에 포함)
        if (normalizedLabel.contains(normalizedTitle) || normalizedTitle.contains(normalizedLabel)) {
            return true;
        }

        // 핵심 키워드 매칭 (2글자 이상 키워드가 공통으로 있으면 매칭)
        String[] titleWords = normalizedTitle.split("\\s+");
        for (String word : titleWords) {
            if (word.length() >= 2 && normalizedLabel.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String text) {
        return text.toLowerCase()
                .replaceAll("[^가-힣a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static void fillSingleCoverLetter(Page page, String coverLetter) {
        if (coverLetter == null || coverLetter.isBlank()) return;

        Locator textareas = page.locator("textarea:visible");
        for (int i = 0; i < textareas.count(); i++) {
            Locator ta = textareas.nth(i);
            String name = orEmpty(ta.getAttribute("name"));
            String placeholder = orEmpty(ta.getAttribute("placeholder"));

            if (isRelevantField(name, placeholder)) {
                ta.fill(coverLetter);
                log.info("[CoverLetterFiller] 단일 자소서 입력 (관련 textarea: {})", name);
                return;
            }
        }

        // 관련 필드 못 찾으면 첫 번째 빈 textarea
        for (int i = 0; i < textareas.count(); i++) {
            if (textareas.nth(i).inputValue().isBlank()) {
                textareas.nth(i).fill(coverLetter);
                log.info("[CoverLetterFiller] 단일 자소서 입력 (첫 번째 빈 textarea)");
                return;
            }
        }

        fillContentEditable(page, coverLetter);
    }

    private static void fillContentEditable(Page page, String text) {
        Locator editableDiv = page.locator("[contenteditable='true']:visible");
        if (editableDiv.count() > 0) {
            editableDiv.first().fill(text);
            log.info("[CoverLetterFiller] contenteditable에 입력");
        }
    }

    private static boolean isRelevantField(String name, String placeholder) {
        String combined = (name + placeholder).toLowerCase();
        return combined.contains("cover") || combined.contains("자소서") || combined.contains("자기소개")
                || combined.contains("지원동기") || combined.contains("self") || combined.contains("intro");
    }

    private static String combineSections(List<CoverLetterSection> sections) {
        return sections.stream()
                .map(s -> "[" + s.title() + "]\n" + s.content())
                .collect(Collectors.joining("\n\n"));
    }

    static List<CoverLetterSection> parseSections(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("[CoverLetterFiller] 섹션 JSON 파싱 실패: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private static String orEmpty(String value) {
        return value != null ? value : "";
    }
}
