package com.portfolio.jobcrawler.infrastructure.crawler.parser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.portfolio.jobcrawler.infrastructure.crawler.dto.CrawledJobData;
import com.portfolio.jobcrawler.infrastructure.crawler.parser.category.LinkareerJobCategory;
import com.portfolio.jobcrawler.global.util.HtmlSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 링커리어 DOM 파싱 전담 클래스.
 * React SPA이므로 JS evaluate로 데이터 추출.
 */
@Slf4j
@Component
public class LinkareerParser implements SiteParser {

    private static final String LINKAREER_BASE_URL = "https://linkareer.com";

    @Override
    public String getSiteName() {
        return "LINKAREER";
    }

    @Override
    public String buildSearchUrl(String keyword, String jobCategory, String companyType) {
        StringBuilder urlBuilder = new StringBuilder(LINKAREER_BASE_URL);
        urlBuilder.append("/list/recruit?filterBy_activityTypeID=5");
        urlBuilder.append("&filterBy_status=OPEN");
        urlBuilder.append("&orderBy_direction=DESC&orderBy_field=RECENT");
        urlBuilder.append("&page=1");

        if (keyword != null && !keyword.isBlank()) {
            urlBuilder.append("&filterBy_keyword=").append(keyword);
        }

        if ("public".equalsIgnoreCase(companyType)) {
            urlBuilder.append("&filterBy_orgTypeIDs=3");
        }

        return urlBuilder.toString();
    }

    @Override
    public Locator getListItems(Page page) {
        return page.locator("a.recruit-link");
    }

    @Override
    public void waitForListLoaded(Page page) {
        try {
            // React 렌더링 완료까지 .recruit-name이 나올 때까지 대기
            page.waitForSelector(".recruit-name",
                    new Page.WaitForSelectorOptions().setTimeout(15000));
        } catch (Exception e) {
            log.warn("[링커리어-Parser] 공고 목록 로딩 지연 또는 없음: {}", e.getMessage());
        }
    }

    @Override
    public boolean goToNextPage(Page page, int currentPageNum) {
        int nextPage = currentPageNum + 1;
        String currentUrl = page.url();
        String nextUrl;
        if (currentUrl.contains("page=")) {
            nextUrl = currentUrl.replaceAll("page=\\d+", "page=" + nextPage);
        } else {
            nextUrl = currentUrl + "&page=" + nextPage;
        }

        log.info("[링커리어-Parser] {}페이지로 이동", nextPage);
        page.navigate(nextUrl);
        waitForListLoaded(page);

        Locator items = getListItems(page);
        boolean hasItems = items.count() > 0;
        if (!hasItems) {
            log.info("[링커리어-Parser] 더 이상 페이지 없음");
        }
        return hasItems;
    }

    @Override
    public boolean supportsTwoPhase() {
        return true;
    }

    @Override
    public String extractDetailUrl(Page listPage, Locator item) {
        try {
            String href = item.getAttribute("href");
            if (href != null && href.contains("/activity/")) {
                return LINKAREER_BASE_URL + href;
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public CrawledJobData parseListData(Page listPage, Locator item, String requestedJobCategory) {
        String detailUrl = extractDetailUrl(listPage, item);
        if (detailUrl == null) return null;

        // 링커리어는 리스트에서 URL만 추출 가능, 나머지는 상세 페이지에서 채움
        return CrawledJobData.builder()
                .title("").company("").companyLogoUrl("")
                .location("").url(detailUrl).sourceSite(getSiteName())
                .applicationMethod("HOMEPAGE").education("").career("")
                .salary("").deadline("").techStack("")
                .jobCategory(requestedJobCategory != null ? requestedJobCategory : "")
                .description("").requirements("").companyImages("")
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void enrichFromDetailPage(Page detailPage, CrawledJobData data) {
        try {
            // React 렌더링 대기
            try {
                detailPage.waitForSelector("h1", new Page.WaitForSelectorOptions().setTimeout(8_000));
            } catch (Exception e) {
                log.debug("[링커리어-Parser] 상세 페이지 로딩 실패: {}", data.getUrl());
                return;
            }

            Map<String, Object> extracted = (Map<String, Object>) detailPage.evaluate("""
                (() => {
                    const result = {};
                    result.title = document.querySelector('h1')?.innerText?.trim() || '';
                    const dts = document.querySelectorAll('dt');
                    const dds = document.querySelectorAll('dd');
                    const meta = {};
                    for (let i = 0; i < Math.min(dts.length, dds.length); i++) {
                        meta[dts[i].innerText.trim()] = dds[i].innerText.trim();
                    }
                    result.companyType = meta['기업형태'] || '';
                    result.careerType = meta['채용형태'] || '';
                    result.location = meta['근무지역'] || '';
                    const rawDeadline = meta['접수기간'] || '';
                    const magamIdx = rawDeadline.indexOf('마감일');
                    if (magamIdx >= 0) {
                        const afterMagam = rawDeadline.substring(magamIdx + 3).trim();
                        const dateMatch = afterMagam.match(/[0-9]{4}[.][0-9]{2}[.][0-9]{2}/);
                        result.deadline = dateMatch ? dateMatch[0] : afterMagam.split(' ')[0];
                    } else {
                        result.deadline = '';
                    }
                    result.jobCategories = meta['모집직무'] || '';
                    result.website = meta['홈페이지'] || '';
                    result.company = document.querySelector('h3.company-info-content-title')?.innerText?.trim() || '';
                    result.descriptionHtml = document.querySelector('.responsive-element')?.innerHTML || '';
                    // 지원서 다운로드 섹션
                    const downloadSection = document.querySelector('section[class*="ActivityDetailTabFileAndManager"]');
                    if (downloadSection) {
                        const h3 = downloadSection.querySelector('h3');
                        const items = downloadSection.querySelectorAll('li');
                        if (h3 && items.length > 0) {
                            let html = '<h3>' + h3.textContent.trim() + '</h3><ul>';
                            items.forEach(li => { html += '<li>' + li.textContent.trim() + '</li>'; });
                            html += '</ul>';
                            result.downloadSection = html;
                        }
                    }
                    const imgs = document.querySelectorAll('.responsive-element img');
                    result.images = Array.from(imgs)
                        .filter(img => img.width > 50 && img.height > 50)
                        .map(img => img.src)
                        .filter(s => s.startsWith('http')
                            && !s.includes('blank') && !s.includes('transparent'))
                        .filter((v, i, a) => a.indexOf(v) === i)
                        .slice(0, 10);
                    return result;
                })()
            """);

            if (extracted == null) return;

            String title = (String) extracted.getOrDefault("title", "");
            if (title.isEmpty()) return;

            String company = (String) extracted.getOrDefault("company", "");
            if (company.isEmpty() && title.startsWith("[")) {
                int end = title.indexOf("]");
                if (end > 0) company = title.substring(1, end).split("\\s")[0];
            }

            String rawHtml = (String) extracted.getOrDefault("descriptionHtml", "");
            String downloadSection = (String) extracted.getOrDefault("downloadSection", "");
            String description = rawHtml.isEmpty() ? "" : HtmlSanitizer.sanitize(rawHtml);
            if (!downloadSection.isEmpty()) description += downloadSection;
            if (description.length() > 10000) description = description.substring(0, 10000);

            String jobCategories = (String) extracted.getOrDefault("jobCategories", "");
            String requestedCategory = data.getJobCategory();
            String finalCategory = (requestedCategory != null && !requestedCategory.isBlank())
                    ? requestedCategory : LinkareerJobCategory.normalizeCategory(jobCategories);

            @SuppressWarnings("unchecked")
            List<String> images = (List<String>) extracted.getOrDefault("images", List.of());

            String location = (String) extracted.getOrDefault("location", "");
            String careerType = (String) extracted.getOrDefault("careerType", "");
            String deadlineValue = (String) extracted.getOrDefault("deadline", "");

            data.enrichBasicInfo(title, company, location);
            data.enrichJobDetail(description, null, String.join(",", images));
            data.enrichConditions(careerType, null, deadlineValue, null);
            data.enrichClassification(finalCategory, jobCategories, null);

            log.info("[링커리어-Parser] 공고 수집: {} - {}", company, title);
        } catch (Exception e) {
            log.warn("[링커리어-Parser] 상세 페이지 보강 실패: {}", e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public CrawledJobData parseJobData(Page listPage, Locator item, String requestedJobCategory) {
        CrawledJobData data = parseListData(listPage, item, requestedJobCategory);
        if (data == null) return null;

        Page detailPage = listPage.context().newPage();
        try {
            detailPage.setDefaultNavigationTimeout(15_000);
            detailPage.navigate(data.getUrl(), new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
            enrichFromDetailPage(detailPage, data);
            // 제목이 비어있으면 상세 페이지에서 데이터를 못 가져온 것
            if (data.getTitle() == null || data.getTitle().isEmpty()) return null;
        } catch (Exception e) {
            log.warn("[링커리어-Parser] 상세 페이지 파싱 실패 ({}): {}", data.getUrl(), e.getMessage());
            return null;
        } finally {
            detailPage.close();
        }
        return data;
    }
}
