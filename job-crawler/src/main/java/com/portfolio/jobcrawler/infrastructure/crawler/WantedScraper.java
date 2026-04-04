package com.portfolio.jobcrawler.infrastructure.crawler;

import com.portfolio.jobcrawler.infrastructure.crawler.core.PlaywrightScrapingEngine;
import com.portfolio.jobcrawler.infrastructure.crawler.dto.CrawledJobData;
import com.portfolio.jobcrawler.infrastructure.crawler.parser.WantedParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 원티드 크롤러 (Composition 패턴)
 * - 브라우저 조작: PlaywrightScrapingEngine
 * - DOM 파싱: WantedParser
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WantedScraper implements JobScraper {

    private final PlaywrightScrapingEngine scrapingEngine;
    private final WantedParser parser;

    @Override
    public String getSiteName() {
        return parser.getSiteName();
    }

    @Override
    public List<CrawledJobData> scrapeJobs(String keyword, String jobCategory, int maxPages, String companyType) {
        log.info("[WantedScraper] 원티드 크롤링 시작 - maxPages: {}", maxPages <= 0 ? "무제한" : maxPages);
        return scrapingEngine.scrape(parser, keyword, jobCategory, maxPages, 60_000, companyType);
    }
}
