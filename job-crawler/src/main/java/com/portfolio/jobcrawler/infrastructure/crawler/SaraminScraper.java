package com.portfolio.jobcrawler.infrastructure.crawler;

import com.portfolio.jobcrawler.infrastructure.crawler.core.PlaywrightScrapingEngine;
import com.portfolio.jobcrawler.infrastructure.crawler.dto.CrawledJobData;
import com.portfolio.jobcrawler.infrastructure.crawler.parser.SaraminParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 사람인 크롤러 (AGENTS.md 원칙 적용 리팩토링)
 * - 상속 기반(BasePlaywrightScraper) 제거 -> 의존성 역전 및 조합(Composition) 활용
 * - 브라우저 조작 로직은 `PlaywrightScrapingEngine`에, DOM 파싱은 `SaraminParser`에 위임
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SaraminScraper implements JobScraper {

    private final PlaywrightScrapingEngine scrapingEngine;
    private final SaraminParser parser;

    @Override
    public String getSiteName() {
        return parser.getSiteName();
    }

    @Override
    public List<CrawledJobData> scrapeJobs(String keyword, String jobCategory, int maxPages) {
        log.info("[SaraminScraper] 사람인 크롤링 시작 위임 - maxPages: {}", maxPages <= 0 ? "무제한" : maxPages);
        return scrapingEngine.scrape(parser, keyword, jobCategory, maxPages, 60_000);
    }
}
