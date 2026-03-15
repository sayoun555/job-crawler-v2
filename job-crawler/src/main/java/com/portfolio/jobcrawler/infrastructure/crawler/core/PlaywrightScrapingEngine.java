package com.portfolio.jobcrawler.infrastructure.crawler.core;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.portfolio.jobcrawler.infrastructure.crawler.PlaywrightManager;
import com.portfolio.jobcrawler.infrastructure.crawler.dto.CrawledJobData;
import com.portfolio.jobcrawler.infrastructure.crawler.parser.SiteParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 브라우저를 띄우고 페이지를 순회하며 크롤링하는 `행위(동작)` 자체를 캡슐화한 엔진 클래스.
 * 구체적인 DOM 선택이나 파싱(SiteParser)은 외부에서 주입(Composition)받아 수행한다. (SRP, OCP, 상속체계 탈피)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlaywrightScrapingEngine {

    private final PlaywrightManager playwrightManager;

    /**
     * 특정 파서를 사용하여 크롤링을 수행한다.
     * @param parser 데이터를 파싱하는 책임을 지는 도메인 객체
     * @param keyword 검색 키워드
     * @param maxPages 최대 순회할 페이지 수 (0 이하이면 무제한 - 공고가 없을 때까지 계속)
     * @param timeoutMs 각 페이지 이동 시 타임아웃
     * @return 크롤링된 공고 목록
     */
    public List<CrawledJobData> scrape(SiteParser parser, String keyword, String jobCategory, int maxPages, int timeoutMs) {
        String siteName = parser.getSiteName();
        boolean unlimited = maxPages <= 0;
        log.info("[{}] 크롤링 시작 - 키워드: {}, 카테고리: {}, 최대페이지: {}", siteName, keyword, jobCategory, unlimited ? "무제한" : maxPages);
        List<CrawledJobData> results = new ArrayList<>();

        try (BrowserContext context = playwrightManager.createStealthContext()) {
            Page page = setupPage(context, timeoutMs);
            navigateToSearchUrl(page, parser, siteName, keyword, jobCategory);

            for (int pageNum = 1; unlimited || pageNum <= maxPages; pageNum++) {
                boolean hasMorePages = processSinglePage(page, parser, siteName, pageNum, results, jobCategory);
                if (!hasMorePages) {
                    break;
                }
            }
            page.close();
        } catch (Exception e) {
            log.error("[{}] 크롤링 실패: {}", siteName, e.getMessage());
        }
        
        log.info("[{}] 크롤링 완료 - {} 건", siteName, results.size());
        return results;
    }

    private Page setupPage(BrowserContext context, int timeoutMs) {
        Page page = context.newPage();
        page.setDefaultNavigationTimeout(timeoutMs);
        return page;
    }

    private void navigateToSearchUrl(Page page, SiteParser parser, String siteName, String keyword, String jobCategory) {
        String url = parser.buildSearchUrl(keyword, jobCategory);
        page.navigate(url);
        parser.waitForListLoaded(page);
    }

    private boolean processSinglePage(Page page, SiteParser parser, String siteName, int pageNum, List<CrawledJobData> results, String jobCategory) {
        log.info("[{}] {} 페이지 크롤링", siteName, pageNum);
        
        try {
            Locator items = parser.getListItems(page);
            int count = items.count();
            log.info("[{}] 발견된 공고 수 (DOM 탐색): {}", siteName, count);
            
            if (count == 0) {
                log.info("[{}] 더 이상 공고가 없습니다. 크롤링을 종료합니다.", siteName);
                log.info("[{}] (DEBUG) 현재 로드된 페이지 DOM 스니펫: \n{}", siteName, page.content().substring(0, Math.min(2000, page.content().length())));
                return false;
            }

            extractAllItemsOnPage(page, parser, items, count, siteName, results, jobCategory);

            return parser.goToNextPage(page, pageNum);
        } catch (Exception e) {
            log.error("[{}] {} 페이지 에러: {}", siteName, pageNum, e.getMessage());
            return false;
        }
    }

    private void extractAllItemsOnPage(Page page, SiteParser parser, Locator items, int count, String siteName, List<CrawledJobData> results, String jobCategory) {
        for (int i = 0; i < count; i++) {
            try {
                CrawledJobData jobData = parser.parseJobData(page, items.nth(i), jobCategory);
                if (jobData != null) {
                    results.add(jobData);
                }
            } catch (Exception e) {
                log.warn("[{}] 공고 파싱 실패 ({}): {}", siteName, i, e.getMessage());
            }
        }
    }
}
