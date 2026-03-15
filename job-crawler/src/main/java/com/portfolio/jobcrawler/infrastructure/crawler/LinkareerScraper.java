package com.portfolio.jobcrawler.infrastructure.crawler;

import com.portfolio.jobcrawler.infrastructure.crawler.core.PlaywrightScrapingEngine;
import com.portfolio.jobcrawler.infrastructure.crawler.dto.CrawledJobData;
import com.portfolio.jobcrawler.infrastructure.crawler.parser.LinkareerParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 링커리어 크롤러 (AGENTS.md 원칙 적용)
 * - 상속 X → 의존성 역전 및 조합(Composition) 활용
 * - 브라우저 조작: PlaywrightScrapingEngine / DOM 파싱: LinkareerParser
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkareerScraper implements JobScraper {

    private final PlaywrightScrapingEngine scrapingEngine;
    private final LinkareerParser parser;

    @Override
    public String getSiteName() {
        return parser.getSiteName();
    }

    @Override
    public List<CrawledJobData> scrapeJobs(String keyword, String jobCategory, int maxPages) {
        log.info("[LinkareerScraper] 링커리어 크롤링 시작 위임 - maxPages: {}", maxPages <= 0 ? "무제한" : maxPages);
        return scrapingEngine.scrape(parser, keyword, jobCategory, maxPages, 30_000);
    }
}
