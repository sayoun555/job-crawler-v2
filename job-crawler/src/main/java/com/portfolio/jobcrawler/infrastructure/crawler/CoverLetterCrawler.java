package com.portfolio.jobcrawler.infrastructure.crawler;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.portfolio.jobcrawler.infrastructure.crawler.dto.CrawledCoverLetterData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CoverLetterCrawler {

    private static final String BASE_URL = "https://linkareer.com";
    private static final String LIST_URL_TEMPLATE = BASE_URL + "/cover-letter/search?page=%d&sort=%s&tab=all";
    private static final String[] SORT_MODES = {"PASSED_AT", "RECENT_SCRAP_COUNT"};

    private final PlaywrightManager playwrightManager;

    public List<CrawledCoverLetterData> crawl(int maxPages) {
        List<CrawledCoverLetterData> results = new ArrayList<>();
        log.info("[자소서 크롤러] 크롤링 시작 - maxPages: {}", maxPages <= 0 ? "무제한" : maxPages);

        try (BrowserContext context = playwrightManager.createStealthContext()) {
            Page page = context.newPage();
            page.setDefaultNavigationTimeout(30_000);

            for (String sortMode : SORT_MODES) {
                log.info("[자소서 크롤러] 정렬 모드: {}", sortMode);
                crawlBySort(page, sortMode, maxPages, results);
            }

            page.close();
        } catch (Exception e) {
            log.error("[자소서 크롤러] 크롤링 실패: {}", e.getMessage());
        }

        log.info("[자소서 크롤러] 크롤링 완료 - {}건", results.size());
        return results;
    }

    private void crawlBySort(Page page, String sortMode, int maxPages, List<CrawledCoverLetterData> results) {
        for (int pageNum = 1; maxPages <= 0 || pageNum <= maxPages; pageNum++) {
            String url = String.format(LIST_URL_TEMPLATE, pageNum, sortMode);
            page.navigate(url);

            try {
                page.waitForSelector("a.link[href*='/cover-letter/']",
                        new Page.WaitForSelectorOptions().setTimeout(10_000));
            } catch (Exception e) {
                log.info("[자소서 크롤러] 더 이상 페이지 없음 ({} / {}페이지)", sortMode, pageNum);
                break;
            }

            Locator items = page.locator("a.link[href*='/cover-letter/']");
            int count = items.count();
            log.info("[자소서 크롤러] {} / {}페이지 - {}개 항목 발견", sortMode, pageNum, count);

            if (count == 0) break;

            List<String> hrefs = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String href = items.nth(i).getAttribute("href");
                if (href != null && href.startsWith("/cover-letter/")) {
                    hrefs.add(BASE_URL + href.split("\\?")[0]);
                }
            }

            for (String detailUrl : hrefs) {
                try {
                    CrawledCoverLetterData data = scrapeDetail(page, detailUrl);
                    if (data != null) {
                        results.add(data);
                    }
                } catch (Exception e) {
                    log.warn("[자소서 크롤러] 상세 페이지 실패 ({}): {}", detailUrl, e.getMessage());
                }
            }
        }
    }

    private CrawledCoverLetterData scrapeDetail(Page page, String detailUrl) {
        page.navigate(detailUrl, new Page.NavigateOptions()
                .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));

        try {
            page.waitForSelector("h1.basic-info", new Page.WaitForSelectorOptions().setTimeout(5_000));
        } catch (Exception e) {
            return null;
        }

        // 기본 정보 파싱: "삼성전자 / DS부문_메모리 / 2025 상반기"
        String basicInfo = safeText(page, "h1.basic-info");
        String[] basicParts = basicInfo.split("\\s+/\\s+");

        String company = basicParts.length > 0 ? basicParts[0].trim() : "";
        String position = basicParts.length > 1 ? basicParts[1].trim() : "";
        String period = basicParts.length > 2 ? basicParts[2].trim() : "";

        // 기업유형/채용형태는 리스트의 mobile-basic-info에서 추출했지만,
        // 상세 페이지에서는 basic-info에 없을 수 있으므로 빈 값 허용
        String companyType = "";
        String careerType = "";
        if (basicParts.length > 3) {
            String extra = basicParts[3].trim();
            String[] typeParts = extra.split(",");
            for (String part : typeParts) {
                String trimmed = part.trim();
                if (trimmed.contains("대기업") || trimmed.contains("중견") || trimmed.contains("공기업") || trimmed.contains("기타")) {
                    companyType = trimmed;
                } else {
                    careerType = careerType.isEmpty() ? trimmed : careerType + ", " + trimmed;
                }
            }
        }

        // 스펙 정보 파싱: "성균관대 / 화학과 / 학점 4.05/4.5 / ..."
        // 구분자는 " / " (양쪽에 공백 있는 슬래시). "4.05/4.5"의 슬래시는 공백 없으므로 매칭 안 됨.
        String specInfo = safeText(page, "h3.spec-info");
        String[] specParts = specInfo.split("\\s+/\\s+");

        String school = specParts.length > 0 ? specParts[0].trim() : "";
        String major = specParts.length > 1 ? specParts[1].trim() : "";
        String gpa = specParts.length > 2 ? specParts[2].trim() : "";
        // 나머지를 specs로 합침
        StringBuilder specsBuilder = new StringBuilder();
        for (int i = 3; i < specParts.length; i++) {
            if (specsBuilder.length() > 0) specsBuilder.append(" / ");
            specsBuilder.append(specParts[i].trim());
        }

        // 자소서 본문
        String content = safeText(page, "article");
        if (content.isEmpty()) {
            return null;
        }
        // 연속 빈 줄 정리
        content = content.trim().replaceAll("\\n{3,}", "\n\n");
        if (content.length() > 10000) {
            content = content.substring(0, 10000) + "...";
        }

        if (company.isEmpty()) return null;

        return CrawledCoverLetterData.builder()
                .company(company)
                .position(position)
                .period(period)
                .companyType(companyType)
                .careerType(careerType)
                .school(school)
                .major(major)
                .gpa(gpa)
                .specs(specsBuilder.toString())
                .content(content)
                .scrapCount(0)
                .sourceUrl(detailUrl)
                .build();
    }

    private String safeText(Page page, String selector) {
        try {
            Locator loc = page.locator(selector);
            if (loc.count() > 0) {
                String text = loc.first().innerText();
                return text != null ? text.trim() : "";
            }
        } catch (Exception ignored) {}
        return "";
    }
}
