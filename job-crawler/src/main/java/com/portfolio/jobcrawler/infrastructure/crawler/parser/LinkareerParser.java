package com.portfolio.jobcrawler.infrastructure.crawler.parser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.portfolio.jobcrawler.infrastructure.crawler.dto.CrawledJobData;
import com.portfolio.jobcrawler.infrastructure.crawler.parser.category.LinkareerJobCategory;
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
    public String buildSearchUrl(String keyword, String jobCategory) {
        StringBuilder urlBuilder = new StringBuilder(LINKAREER_BASE_URL);
        urlBuilder.append("/list/recruit?filterBy_activityTypeID=5");
        urlBuilder.append("&filterBy_status=OPEN");
        urlBuilder.append("&orderBy_direction=DESC&orderBy_field=RECENT");
        urlBuilder.append("&page=1");

        if (keyword != null && !keyword.isBlank()) {
            urlBuilder.append("&filterBy_keyword=").append(keyword);
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
    @SuppressWarnings("unchecked")
    public CrawledJobData parseJobData(Page listPage, Locator item, String requestedJobCategory) {
        // a.recruit-link에서 href 추출
        String href = null;
        try {
            href = item.getAttribute("href");
        } catch (Exception ignored) {}

        if (href == null || !href.contains("/activity/")) {
            return null;
        }

        String detailUrl = LINKAREER_BASE_URL + href;

        // 상세 페이지에서 모든 데이터 추출
        Page detailPage = listPage.context().newPage();
        try {
            detailPage.setDefaultNavigationTimeout(15_000);
            detailPage.navigate(detailUrl, new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));

            // React 렌더링 대기
            try {
                detailPage.waitForSelector("h1", new Page.WaitForSelectorOptions().setTimeout(8_000));
            } catch (Exception e) {
                log.debug("[링커리어-Parser] 상세 페이지 로딩 실패: {}", detailUrl);
                return null;
            }

            // JS로 한 번에 데이터 추출 (React DOM 직접 접근)
            Map<String, Object> data = (Map<String, Object>) detailPage.evaluate("""
                (() => {
                    const result = {};
                    result.title = document.querySelector('h1')?.innerText?.trim() || '';

                    // dt/dd 쌍에서 메타 정보 추출
                    const dts = document.querySelectorAll('dt');
                    const dds = document.querySelectorAll('dd');
                    const meta = {};
                    for (let i = 0; i < Math.min(dts.length, dds.length); i++) {
                        meta[dts[i].innerText.trim()] = dds[i].innerText.trim();
                    }
                    result.companyType = meta['기업형태'] || '';
                    result.careerType = meta['채용형태'] || '';
                    result.location = meta['근무지역'] || '';
                    result.deadline = meta['접수기간'] || '';
                    result.jobCategories = meta['모집직무'] || '';
                    result.website = meta['홈페이지'] || '';

                    // 기업 정보
                    result.company = document.querySelector('h3.company-info-content-title')?.innerText?.trim() || '';

                    // 상세 내용
                    result.description = document.querySelector('.responsive-element')?.innerText?.trim() || '';

                    // 이미지
                    const imgs = document.querySelectorAll('.responsive-element img');
                    result.images = Array.from(imgs).map(img => img.src).filter(s => s.startsWith('http')).slice(0, 10);

                    return result;
                })()
            """);

            if (data == null) return null;

            String title = (String) data.getOrDefault("title", "");
            if (title.isEmpty()) return null;

            String company = (String) data.getOrDefault("company", "");
            // 제목에서 회사명 추출 (예: [삼성전자 DX부문] 2026년...)
            if (company.isEmpty() && title.startsWith("[")) {
                int end = title.indexOf("]");
                if (end > 0) company = title.substring(1, end).split("\\s")[0];
            }

            String description = (String) data.getOrDefault("description", "");
            description = description.replaceAll("\\n{3,}", "\n\n");
            if (description.length() > 5000) description = description.substring(0, 5000) + "...";

            String jobCategories = (String) data.getOrDefault("jobCategories", "");
            String finalCategory = (requestedJobCategory != null && !requestedJobCategory.isBlank())
                    ? requestedJobCategory : LinkareerJobCategory.normalizeCategory(jobCategories);

            @SuppressWarnings("unchecked")
            List<String> images = (List<String>) data.getOrDefault("images", List.of());
            String companyImages = String.join(",", images);

            String deadline = (String) data.getOrDefault("deadline", "");
            String location = (String) data.getOrDefault("location", "");
            String careerType = (String) data.getOrDefault("careerType", "");

            log.info("[링커리어-Parser] 공고 수집: {} - {}", company, title);

            return CrawledJobData.builder()
                    .title(title)
                    .company(company)
                    .companyLogoUrl("")
                    .location(location)
                    .url(detailUrl)
                    .description(description)
                    .sourceSite(getSiteName())
                    .applicationMethod("HOMEPAGE")
                    .education("")
                    .career(careerType)
                    .salary("")
                    .deadline(deadline)
                    .techStack(jobCategories)
                    .jobCategory(finalCategory)
                    .requirements("")
                    .companyImages(companyImages)
                    .build();
        } catch (Exception e) {
            log.warn("[링커리어-Parser] 상세 페이지 파싱 실패 ({}): {}", detailUrl, e.getMessage());
            return null;
        } finally {
            detailPage.close();
        }
    }
}
