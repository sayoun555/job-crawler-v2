package com.portfolio.jobcrawler.infrastructure.crawler.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.portfolio.jobcrawler.infrastructure.crawler.dto.CrawledJobData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import com.portfolio.jobcrawler.infrastructure.crawler.parser.category.JobPlanetJobCategory;

/**
 * 잡플래닛 DOM 파싱 전담 클래스. BasePlaywrightScraper에서 분리.
 */
@Slf4j
@Component
public class JobPlanetParser implements SiteParser {

    private static final String JOBPLANET_BASE_URL = "https://www.jobplanet.co.kr";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String getSiteName() {
        return "JOBPLANET";
    }

    @Override
    public String buildSearchUrl(String keyword, String jobCategory) {
        StringBuilder urlBuilder = new StringBuilder(JOBPLANET_BASE_URL);
        urlBuilder.append("/search/job?");
        
        boolean hasQuery = false;
        if (keyword != null && !keyword.isBlank()) {
            try {
                urlBuilder.append("query=").append(URLEncoder.encode(keyword, StandardCharsets.UTF_8.toString()));
                hasQuery = true;
            } catch (Exception e) {}
        }
        
        if (jobCategory != null && !jobCategory.isBlank() && !jobCategory.equals("전체") && !jobCategory.equals("all")) {
            String catId = JobPlanetJobCategory.getCodeByDisplayName(jobCategory);
            if (catId != null) {
                if (hasQuery) urlBuilder.append("&");
                urlBuilder.append("category_ids%5B%5D=").append(catId);
                hasQuery = true;
            } else {
                // ID 매핑이 없으면 키워드 쿼리로 결합
                try {
                    String encodedCat = URLEncoder.encode(jobCategory, StandardCharsets.UTF_8.toString());
                    if (hasQuery) urlBuilder.append("+").append(encodedCat);
                    else urlBuilder.append("query=").append(encodedCat);
                    hasQuery = true;
                } catch (Exception e) {}
            }
        }
        
        if (hasQuery) {
            urlBuilder.append("&order_by=recent");
        } else {
            urlBuilder.append("order_by=recent");
        }
        
        return urlBuilder.toString();
    }

    @Override
    public Locator getListItems(Page page) {
        Locator items = page.locator("a.group.z-0.block");
        if (items.count() == 0) {
            items = page.locator("a[href*='posting_ids']");
        }
        return items;
    }

    @Override
    public void waitForListLoaded(Page page) {
        try {
            page.waitForSelector("a.group.z-0.block, a[href*='posting_ids']",
                    new Page.WaitForSelectorOptions().setTimeout(15000));
        } catch (Exception e) {
            log.warn("[잡플래닛-Parser] 공고 목록 로딩 지연 또는 없음: {}", e.getMessage());
        }
    }

    @Override
    public boolean goToNextPage(Page page, int currentPageNum) {
        Locator nextBtn = page.locator("button:has-text('다음'), a:has-text('다음'), [aria-label='Next']");
        if (nextBtn.count() > 0) {
            nextBtn.first().click();
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {} // PlaywrightManager.longDelay 대체
            return true;
        }
        return false;
    }

    @Override
    public boolean supportsTwoPhase() {
        return true;
    }

    @Override
    public String extractDetailUrl(Page listPage, Locator item) {
        return extractJobUrl(item);
    }

    @Override
    public CrawledJobData parseListData(Page listPage, Locator item, String requestedJobCategory) {
        String title = safeText(item, "h4");
        String company = safeText(item, "em");
        String companyLogoUrl = extractCompanyLogo(item);

        if (title.isEmpty() || company.isEmpty()) {
            return null;
        }

        String jobUrl = extractJobUrl(item);
        List<String> techList = new ArrayList<>();
        String career = extractCareerAndTechStack(item, techList);

        String finalCategory;
        if (requestedJobCategory != null && !requestedJobCategory.isBlank() && !requestedJobCategory.equals("전체") && !requestedJobCategory.equals("all")) {
            finalCategory = requestedJobCategory;
        } else {
            finalCategory = "기타";
        }

        return CrawledJobData.builder()
                .title(title).company(company).companyLogoUrl(companyLogoUrl)
                .location("").url(jobUrl).sourceSite(getSiteName())
                .applicationMethod("UNKNOWN").education("").career(career)
                .salary("").jobCategory(finalCategory).deadline("")
                .techStack(String.join(",", techList))
                .description("").requirements("").companyImages("")
                .build();
    }

    @Override
    public void enrichFromDetailPage(Page detailPage, CrawledJobData data) {
        try {
            // 잡플래닛 상세 페이지 렌더링 대기
            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}

            StringBuilder descBuilder = new StringBuilder();
            extractSummaryFromJsonBoxForData(detailPage, descBuilder, data);
            extractDetailSections(detailPage, descBuilder);

            String description = descBuilder.toString().trim();
            if (description.length() > 5000) {
                description = description.substring(0, 5000) + "...";
            }
            data.setDescription(description);
            data.setCompanyImages("");
        } catch (Exception e) {
            log.warn("[잡플래닛-Parser] 상세 페이지 보강 실패: {}", e.getMessage());
        }
    }

    @Override
    public CrawledJobData parseJobData(Page listPage, Locator item, String requestedJobCategory) {
        CrawledJobData data = parseListData(listPage, item, requestedJobCategory);
        if (data == null || data.getUrl() == null || data.getUrl().isBlank()) return data;

        Page detailPage = listPage.context().newPage();
        try {
            detailPage.setDefaultNavigationTimeout(60_000);
            detailPage.navigate(data.getUrl());
            enrichFromDetailPage(detailPage, data);
        } catch (Exception e) {
            log.warn("[잡플래닛-Parser] 상세 페이지 크롤링 실패 ({}): {}", data.getUrl(), e.getMessage());
        } finally {
            detailPage.close();
        }
        return data;
    }

    // normalizeCategory replaced by inline Enum calls where applicable

    private String extractJobUrl(Locator item) {
        String jobUrl = item.getAttribute("href");
        if (jobUrl != null && !jobUrl.startsWith("http")) {
            return JOBPLANET_BASE_URL + jobUrl;
        }
        return jobUrl;
    }

    private String extractCompanyLogo(Locator item) {
        String logoUrl = safeAttr(item, ".company_logo img, .logo img", "src");
        if (logoUrl == null || logoUrl.isEmpty()) {
            logoUrl = safeAttr(item, "img[alt*='로고']", "src"); // Fallback
        }
        return logoUrl != null ? logoUrl : "";
    }

    private String extractCareerAndTechStack(Locator item, List<String> techList) {
        Locator spans = item.locator("span");
        String career = "";
        
        for (int i = 0; i < spans.count(); i++) {
            String text = safeText(spans.nth(i));
            if (text.isEmpty()) {
                continue;
            }
            if (text.matches(".*\\d+년.*")) {
                career = text;
            } else {
                techList.add(text);
            }
        }
        return career;
    }

    private void extractSummaryFromJsonBoxForData(Page detailPage, StringBuilder descBuilder, CrawledJobData data) {
        String summaryJs = """
                (() => {
                    const box = document.querySelector('[class*=recruitment-detail__box]')
                              || document.querySelector('[class*=detail__box]');
                    if (!box) return '{}';
                    const result = {};
                    box.querySelectorAll('dl').forEach(dl => {
                        const dt = dl.querySelector('dt')?.innerText?.trim() || '';
                        const dd = dl.querySelector('dd')?.innerText?.trim() || '';
                        if (dt && dd) result[dt] = dd;
                    });
                    return JSON.stringify(result);
                })();
                """;
        
        String jsonStr = (String) detailPage.evaluate(summaryJs);
        descBuilder.append("[채용 요약]\n");

        try {
            JsonNode node = OBJECT_MAPPER.readTree(jsonStr);
            
            applySummaryField(node, "마감일", data::setDeadline);
            applySummaryField(node, "경력", data::setCareer);
            applySummaryField(node, "근무지역", data::setLocation);
            applySummaryField(node, "스킬", data::setTechStack);

            if (node.has("직무")) {
                String jobCategory = node.get("직무").asText();
                data.setJobCategory(jobCategory);
                descBuilder.append("- 직무: ").append(jobCategory).append("\n");
            }
            if (node.has("고용형태")) {
                descBuilder.append("- 고용형태: ").append(node.get("고용형태").asText()).append("\n");
            }
        } catch (Exception ignored) {
            // Ignore JSON parsing errors
        }
    }

    private void applySummaryField(JsonNode node, String fieldName, java.util.function.Consumer<String> consumer) {
        if (node.has(fieldName)) {
            consumer.accept(node.get(fieldName).asText());
        }
    }

    private void extractDetailSections(Page detailPage, StringBuilder descBuilder) {
        Locator sections = detailPage.locator("h3[class*='tit'], h3[class*='title']");
        
        if (sections.count() > 0) {
            descBuilder.append("\n");
            for (int i = 0; i < sections.count(); i++) {
                Locator h3 = sections.nth(i);
                String secTitle = h3.innerText();
                
                if (secTitle != null && !secTitle.trim().isEmpty()) {
                    descBuilder.append("■ ").append(secTitle.trim()).append("\n");
                    Locator content = detailPage.locator("xpath=//h3[contains(@class, 'title') or contains(@class, 'tit')][normalize-space()='" + secTitle.trim() + "']/following-sibling::div[1]");
                    
                    if (content.count() > 0) {
                        descBuilder.append(content.first().innerText()).append("\n\n");
                    }
                }
            }
        } else {
            extractMainBoxFallback(detailPage, descBuilder);
        }
    }

    private void extractMainBoxFallback(Page detailPage, StringBuilder descBuilder) {
        Locator mainBox = detailPage.locator(".recruitment-detail__content, .detail__content");
        if (mainBox.count() > 0) {
            descBuilder.append("\n").append(mainBox.first().innerText());
        }
    }

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

    private String safeAttr(Locator parent, String selector, String attr) {
        try {
            Locator loc = parent.locator(selector);
            if (loc.count() > 0) {
                return loc.first().getAttribute(attr);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String safeText(Locator locator) {
        try {
            String text = locator.innerText();
            return text != null ? text.trim() : "";
        } catch (Exception ignored) {}
        return "";
    }
}
