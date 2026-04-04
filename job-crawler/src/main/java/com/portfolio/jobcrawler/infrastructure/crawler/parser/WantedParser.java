package com.portfolio.jobcrawler.infrastructure.crawler.parser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.portfolio.jobcrawler.infrastructure.crawler.dto.CrawledJobData;
import com.portfolio.jobcrawler.global.util.HtmlSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 원티드 DOM 파싱 전담 클래스.
 * - 목록: /wdlist/518 (개발 카테고리), 무한 스크롤
 * - 카드: .Card_Card__aaatv → a[href*="/wd/"]
 * - 상세: /wd/{positionId}, "상세 정보 더 보기" 버튼 클릭 후 전체 내용 추출
 */
@Slf4j
@Component
public class WantedParser implements SiteParser {

    private static final String WANTED_BASE_URL = "https://www.wanted.co.kr";

    @Override
    public String getSiteName() {
        return "WANTED";
    }

    @Override
    public String buildSearchUrl(String keyword, String jobCategory, String companyType) {
        StringBuilder urlBuilder = new StringBuilder(WANTED_BASE_URL);
        urlBuilder.append("/wdlist/518?country=kr&job_sort=job.latest_order");
        urlBuilder.append("&years=0&years=5&locations=all");

        if (keyword != null && !keyword.isBlank()) {
            urlBuilder.append("&search=").append(keyword);
        }

        return urlBuilder.toString();
    }

    @Override
    public Locator getListItems(Page page) {
        return page.locator(".Card_Card__aaatv");
    }

    @Override
    public void waitForListLoaded(Page page) {
        try {
            page.waitForSelector("[data-cy='job-card']",
                    new Page.WaitForSelectorOptions().setTimeout(15000));
        } catch (Exception e) {
            log.warn("[원티드-Parser] 공고 목록 로딩 지연: {}", e.getMessage());
        }
    }

    @Override
    public boolean goToNextPage(Page page, int currentPageNum) {
        int beforeCount = getListItems(page).count();

        for (int scroll = 0; scroll < 5; scroll++) {
            page.evaluate("""
                    (() => {
                        const current = window.scrollY;
                        const max = document.body.scrollHeight - window.innerHeight;
                        const step = Math.max(window.innerHeight, (max - current) / 3);
                        window.scrollBy(0, step);
                    })()
                    """);
            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}

            int midCount = getListItems(page).count();
            if (midCount > beforeCount) {
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                return true;
            }
        }

        for (int wait = 0; wait < 8; wait++) {
            int afterCount = getListItems(page).count();
            if (afterCount > beforeCount) {
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                return true;
            }
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        }

        return false;
    }

    @Override
    public boolean supportsTwoPhase() {
        return true;
    }

    @Override
    public String extractDetailUrl(Page listPage, Locator item) {
        String href = safeAttr(item, "a[href*='/wd/']", "href");
        if (href == null || href.isEmpty()) return null;
        if (href.startsWith("/")) return WANTED_BASE_URL + href;
        return href;
    }

    @Override
    public CrawledJobData parseListData(Page listPage, Locator item, String requestedJobCategory) {
        String title = safeText(item, "[class*=JobCard__body__position]");
        if (title.isEmpty()) return null;

        String company = safeText(item, "[class*=CompanyNameWithLocationPeriod__company]");
        String locationAndCareer = safeText(item, "[class*=CompanyNameWithLocationPeriod__location]");

        String location = "";
        String career = "";
        if (!locationAndCareer.isEmpty() && locationAndCareer.contains("·")) {
            String[] parts = locationAndCareer.split("·", 2);
            location = parts[0].trim();
            career = parts.length > 1 ? parts[1].trim() : "";
        } else {
            location = locationAndCareer;
        }

        String url = extractDetailUrl(listPage, item);
        if (url == null) return null;

        String logoUrl = safeAttr(item, "[class*=JobCard__thumb] img", "src");

        return CrawledJobData.builder()
                .title(title).company(company).companyLogoUrl(logoUrl != null ? logoUrl : "")
                .location(location).url(url).sourceSite(getSiteName())
                .applicationMethod("HOMEPAGE")
                .education("").career(career)
                .salary("").deadline("").techStack("")
                .jobCategory(requestedJobCategory != null && !requestedJobCategory.isBlank() ? requestedJobCategory : "개발")
                .description("").requirements("").companyImages("")
                .build();
    }

    @Override
    public CrawledJobData parseJobData(Page listPage, Locator item, String requestedJobCategory) {
        return parseListData(listPage, item, requestedJobCategory);
    }

    @Override
    public void enrichFromDetailPage(Page detailPage, CrawledJobData data) {
        try {
            detailPage.waitForSelector("[class*=JobDescription]",
                    new Page.WaitForSelectorOptions().setTimeout(10_000));

            clickMoreButton(detailPage);

            StringBuilder desc = new StringBuilder();
            StringBuilder req = new StringBuilder();

            enrichHeaderInfo(detailPage, data);
            extractDescriptionSections(detailPage, desc, req);
            extractTags(detailPage, desc, data);
            extractDeadlineAndLocation(detailPage, data);

            data.enrichJobDetail(desc.toString(), req.toString(), "");
        } catch (Exception e) {
            log.warn("[원티드-Parser] 상세 페이지 보강 실패: {}", e.getMessage());
        }
    }

    private void clickMoreButton(Page detailPage) {
        try {
            Locator moreBtn = detailPage.locator("[class*=JobDescription] button");
            if (moreBtn.count() > 0) {
                moreBtn.first().click();
                detailPage.waitForTimeout(500);
            }
        } catch (Exception e) {
            log.debug("[원티드-Parser] 더보기 버튼 클릭 실패 (이미 펼쳐진 상태): {}", e.getMessage());
        }
    }

    private void enrichHeaderInfo(Page detailPage, CrawledJobData data) {
        String title = safePageText(detailPage, "h1");
        String company = safePageText(detailPage, "[class*=JobHeader__Tools__Company__Link]");

        Locator infoSpans = detailPage.locator("[class*=JobHeader__Tools__Company__Info]");
        String location = "";
        String career = "";
        if (infoSpans.count() >= 1) location = infoSpans.first().textContent().trim();
        if (infoSpans.count() >= 2) career = infoSpans.nth(1).textContent().trim();

        if (!title.isEmpty()) data.enrichBasicInfo(title, null, null);
        if (!company.isEmpty()) data.enrichBasicInfo(null, company, null);
        if (!location.isEmpty()) data.enrichBasicInfo(null, null, location);
        if (!career.isEmpty()) data.enrichConditions(career, null, null, null);
    }

    private void extractDescriptionSections(Page detailPage, StringBuilder desc, StringBuilder req) {
        Locator wrapper = detailPage.locator("[class*=JobDescription__paragraph__wrapper]");
        if (wrapper.count() == 0) return;

        // 첫 번째 span: 회사/포지션 소개
        Locator introSpan = wrapper.locator("> span > span").first();
        if (introSpan.count() > 0) {
            String introText = introSpan.textContent().trim()
                    .replace("\n", "<br>")
                    .replaceAll("(?<!<br>)•", "<br>•")
                    .replaceAll("(?<!<br>)(\\d+\\.\\s)", "<br>$1");
            desc.append("<h3>포지션 소개</h3>");
            desc.append("<p>").append(introText).append("</p>");
        }

        Locator paragraphs = wrapper.locator("[class*=JobDescription__paragraph]");
        for (int i = 0; i < paragraphs.count(); i++) {
            String heading = safeText(paragraphs.nth(i), "h3");
            Locator contentSpan = paragraphs.nth(i).locator("span > span");
            String content = contentSpan.count() > 0 ? contentSpan.first().textContent().trim() : "";

            if (heading.isEmpty() || content.isEmpty()) continue;

            desc.append("<h3>").append(heading).append("</h3>");
            String formatted = content
                    .replace("\n", "<br>")
                    .replaceAll("(?<!<br>)•", "<br>•")
                    .replaceAll("(?<!<br>)(\\d+\\.\\s)", "<br>$1");
            desc.append("<p>").append(formatted).append("</p>");

            if (heading.contains("자격요건") || heading.contains("우대사항")) {
                req.append("[").append(heading).append("]\n");
                req.append(content).append("\n\n");
            }
        }
    }

    private void extractTags(Page detailPage, StringBuilder desc, CrawledJobData data) {
        Locator tags = detailPage.locator("[class*=CompanyTagItem] span[class*=nkj4w6]");
        if (tags.count() == 0) return;

        List<String> techTags = new ArrayList<>();
        desc.append("<h3>태그</h3><ul>");
        for (int i = 0; i < tags.count(); i++) {
            String tag = tags.nth(i).textContent().trim();
            desc.append("<li>").append(tag).append("</li>");
            techTags.add(tag);
        }
        desc.append("</ul>");

        data.enrichClassification(null, String.join(",", techTags), null);
    }

    private void extractDeadlineAndLocation(Page detailPage, CrawledJobData data) {
        String deadline = safePageText(detailPage, "[class*=JobDueTime] span");
        if (!deadline.isEmpty()) {
            data.enrichConditions(null, null, deadline, null);
        }

        String location = safePageText(detailPage, "[class*=JobWorkPlace] span");
        if (!location.isEmpty()) {
            data.enrichBasicInfo(null, null, location);
        }
    }

    // ========== 유틸리티 ==========

    private String safeText(Locator parent, String selector) {
        try {
            Locator loc = parent.locator(selector);
            if (loc.count() > 0) {
                String text = loc.first().innerText();
                return text != null ? text.trim() : "";
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String safePageText(Page page, String selector) {
        try {
            Locator loc = page.locator(selector);
            if (loc.count() > 0) {
                String text = loc.first().innerText();
                return text != null ? text.trim() : "";
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String safeAttr(Locator parent, String selector, String attr) {
        try {
            Locator loc = parent.locator(selector);
            if (loc.count() > 0) {
                return loc.first().getAttribute(attr);
            }
        } catch (Exception ignored) {}
        return null;
    }
}
