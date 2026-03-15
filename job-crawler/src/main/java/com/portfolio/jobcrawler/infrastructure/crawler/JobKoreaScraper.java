package com.portfolio.jobcrawler.infrastructure.crawler;

import com.portfolio.jobcrawler.infrastructure.crawler.core.PlaywrightScrapingEngine;
import com.portfolio.jobcrawler.infrastructure.crawler.dto.CrawledJobData;
import com.portfolio.jobcrawler.infrastructure.crawler.parser.JobKoreaParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 잡코리아 크롤러 (Composition 패턴)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobKoreaScraper implements JobScraper {

    private final PlaywrightScrapingEngine scrapingEngine;
    private final JobKoreaParser parser;

    @Override
    public String getSiteName() {
        return parser.getSiteName();
    }

    @Override
    public List<CrawledJobData> scrapeJobs(String keyword, String jobCategory, int maxPages) {
        log.info("[JobKoreaScraper] 잡코리아 크롤링 시작 - maxPages: {}", maxPages <= 0 ? "무제한" : maxPages);
        return scrapingEngine.scrape(parser, keyword, jobCategory, maxPages, 30_000);
    }
}
