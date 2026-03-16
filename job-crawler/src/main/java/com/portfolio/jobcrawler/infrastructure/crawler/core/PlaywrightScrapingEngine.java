package com.portfolio.jobcrawler.infrastructure.crawler.core;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import com.portfolio.jobcrawler.infrastructure.crawler.PlaywrightManager;
import com.portfolio.jobcrawler.infrastructure.crawler.dto.CrawledJobData;
import com.portfolio.jobcrawler.infrastructure.crawler.parser.SiteParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 브라우저를 띄우고 페이지를 순회하며 크롤링하는 행위를 캡슐화한 엔진 클래스.
 * 2단계 크롤링을 지원하는 파서는 리스트 파싱 → 병렬 상세 페이지 보강 순서로 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlaywrightScrapingEngine {

    private final PlaywrightManager playwrightManager;
    private final DuplicateChecker duplicateChecker;

    @Value("${crawler.detail.concurrency:2}")
    private int detailConcurrency;

    private static final int MAX_DETAIL_RETRIES = 3;
    private static final int EMPTY_PAGE_THRESHOLD = 2; // 연속 N페이지 신규 0건이면 조기 종료

    public List<CrawledJobData> scrape(SiteParser parser, String keyword, String jobCategory, int maxPages, int timeoutMs) {
        String siteName = parser.getSiteName();
        boolean unlimited = maxPages <= 0;
        log.info("[{}] 크롤링 시작 - 키워드: {}, 카테고리: {}, 최대페이지: {}", siteName, keyword, jobCategory, unlimited ? "무제한" : maxPages);
        List<CrawledJobData> results = new ArrayList<>();
        int consecutiveEmptyPages = 0;

        try (BrowserContext context = playwrightManager.createStealthContext()) {
            Page page = setupPage(context, timeoutMs);
            navigateToSearchUrl(page, parser, siteName, keyword, jobCategory);

            for (int pageNum = 1; unlimited || pageNum <= maxPages; pageNum++) {
                int beforeSize = results.size();
                boolean hasMorePages = processSinglePage(page, parser, siteName, pageNum, results, jobCategory);

                // 이 페이지에서 신규 공고가 없었으면 카운트 증가
                int newOnThisPage = results.size() - beforeSize;
                if (newOnThisPage == 0) {
                    consecutiveEmptyPages++;
                    log.info("[{}] 신규 공고 없음 (연속 {}/{})", siteName, consecutiveEmptyPages, EMPTY_PAGE_THRESHOLD);
                    if (consecutiveEmptyPages >= EMPTY_PAGE_THRESHOLD) {
                        log.info("[{}] 연속 {}페이지 신규 없음 → 조기 종료 (총 {} 건 수집)", siteName, EMPTY_PAGE_THRESHOLD, results.size());
                        break;
                    }
                } else {
                    consecutiveEmptyPages = 0;
                }

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

            if (parser.supportsTwoPhase()) {
                extractTwoPhase(page, parser, items, count, siteName, results, jobCategory);
            } else {
                extractSequential(page, parser, items, count, siteName, results, jobCategory);
            }

            return parser.goToNextPage(page, pageNum);
        } catch (Exception e) {
            log.error("[{}] {} 페이지 에러: {}", siteName, pageNum, e.getMessage());
            return false;
        }
    }

    /**
     * 기존 순차 방식 (하위 호환)
     */
    private void extractSequential(Page page, SiteParser parser, Locator items, int count, String siteName, List<CrawledJobData> results, String jobCategory) {
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

    /**
     * 2단계 크롤링: 리스트 파싱(순차) → 상세 페이지 보강(병렬 Semaphore)
     */
    private void extractTwoPhase(Page page, SiteParser parser, Locator items, int count, String siteName, List<CrawledJobData> results, String jobCategory) {
        // Phase 1: 리스트에서 기본 데이터 + URL 수집 (빠름, 순차)
        List<CrawledJobData> listItems = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            try {
                CrawledJobData data = parser.parseListData(page, items.nth(i), jobCategory);
                if (data != null) {
                    listItems.add(data);
                }
            } catch (Exception e) {
                log.warn("[{}] 리스트 파싱 실패 ({}): {}", siteName, i, e.getMessage());
            }
        }

        // 중복 필터링: 이미 크롤링된 URL은 상세 페이지를 열지 않음
        List<CrawledJobData> newItems = new ArrayList<>();
        int skipped = 0;
        for (CrawledJobData item : listItems) {
            if (item.getUrl() != null && duplicateChecker.isDuplicate(item.getUrl(), item.getSourceSite())) {
                skipped++;
            } else {
                newItems.add(item);
            }
        }

        log.info("[{}] Phase 1 완료 - 리스트 {} 건 중 신규 {} 건 (중복 {} 건 스킵)",
                siteName, listItems.size(), newItems.size(), skipped);

        if (newItems.isEmpty()) {
            return;
        }

        // Phase 2: 신규 공고만 상세 페이지 병렬 보강 (스레드별 독립 Playwright 인스턴스)
        fetchDetailsWithIsolatedWorkers(parser, siteName, newItems);

        results.addAll(newItems);
    }

    /**
     * 스레드별 독립 Playwright 인스턴스를 사용한 병렬 상세 페이지 보강.
     * 공식 문서 권장 패턴: 각 스레드가 자신만의 Playwright+Browser를 생성하여
     * WebSocket 연결 충돌 없이 완전한 병렬 처리를 수행한다.
     */
    private void fetchDetailsWithIsolatedWorkers(SiteParser parser, String siteName, List<CrawledJobData> items) {
        List<CrawledJobData> itemsNeedingDetail = items.stream()
                .filter(d -> d.getUrl() != null && !d.getUrl().isBlank())
                .toList();

        if (itemsNeedingDetail.isEmpty()) return;

        int concurrency = Math.min(detailConcurrency, itemsNeedingDetail.size());
        log.info("[{}] 상세 페이지 병렬 보강 시작 ({} 건, 워커 {}개)", siteName, itemsNeedingDetail.size(), concurrency);

        // 아이템을 워커별로 라운드-로빈 분배
        @SuppressWarnings("unchecked")
        List<CrawledJobData>[] workerBatches = new List[concurrency];
        for (int i = 0; i < concurrency; i++) {
            workerBatches[i] = new ArrayList<>();
        }
        for (int i = 0; i < itemsNeedingDetail.size(); i++) {
            workerBatches[i % concurrency].add(itemsNeedingDetail.get(i));
        }

        boolean needsFullLoad = "JOBKOREA".equals(siteName) || "SARAMIN".equals(siteName);
        WaitUntilState waitUntil = needsFullLoad ? WaitUntilState.LOAD : WaitUntilState.DOMCONTENTLOADED;
        int timeout = needsFullLoad ? 25_000 : 15_000;

        int[] totalSuccess = {0};
        int[] totalFail = {0};

        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        List<Future<?>> futures = new ArrayList<>();

        for (int w = 0; w < concurrency; w++) {
            final List<CrawledJobData> batch = workerBatches[w];
            final int workerIdx = w;

            futures.add(pool.submit(() -> {
                // 각 스레드가 독립 Playwright+Browser를 생성 (공식 권장 패턴)
                try (PlaywrightManager.PlaywrightWorker worker = playwrightManager.createIsolatedWorker()) {
                    try (BrowserContext ctx = worker.createStealthContext()) {
                        log.info("[{}] 워커-{} 시작 ({} 건, 독립 브라우저)", siteName, workerIdx, batch.size());
                        int success = 0;
                        int fail = 0;

                        for (int i = 0; i < batch.size(); i++) {
                            CrawledJobData data = batch.get(i);
                            boolean enriched = fetchSingleDetail(ctx, parser, siteName, data, waitUntil, timeout);
                            if (enriched) {
                                success++;
                            } else {
                                fail++;
                            }
                            if (i < batch.size() - 1) {
                                playwrightManager.randomDelay(800, 2000);
                            }
                        }

                        synchronized (totalSuccess) {
                            totalSuccess[0] += success;
                            totalFail[0] += fail;
                        }
                        log.info("[{}] 워커-{} 완료 (성공 {}, 실패 {})", siteName, workerIdx, success, fail);
                    }
                } catch (Exception e) {
                    log.error("[{}] 워커-{} 오류: {}", siteName, workerIdx, e.getMessage());
                }
            }));
        }

        for (Future<?> f : futures) {
            try {
                f.get(10, TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                log.error("[{}] 워커 타임아웃", siteName);
            } catch (Exception e) {
                log.error("[{}] 워커 실패: {}", siteName, e.getMessage());
            }
        }

        pool.shutdown();
        log.info("[{}] 상세 페이지 보강 완료 - 성공 {}, 실패 {} / 총 {} 건",
                siteName, totalSuccess[0], totalFail[0], itemsNeedingDetail.size());
    }

    private boolean fetchSingleDetail(BrowserContext ctx, SiteParser parser, String siteName,
                                       CrawledJobData data, WaitUntilState waitUntil, int timeout) {
        for (int attempt = 1; attempt <= MAX_DETAIL_RETRIES; attempt++) {
            Page detailPage = ctx.newPage();
            try {
                detailPage.setDefaultNavigationTimeout(timeout);
                detailPage.navigate(data.getUrl(), new Page.NavigateOptions()
                        .setWaitUntil(waitUntil));
                parser.enrichFromDetailPage(detailPage, data);
                return true;
            } catch (Exception e) {
                log.warn("[{}] 상세 페이지 실패 (시도 {}/{}) - {}: {}",
                        siteName, attempt, MAX_DETAIL_RETRIES, data.getUrl(), e.getMessage());
                if (attempt < MAX_DETAIL_RETRIES) {
                    playwrightManager.randomDelay(2000 * attempt, 4000 * attempt);
                }
            } finally {
                try { detailPage.close(); } catch (Exception ignored) {}
            }
        }
        return false;
    }
}
