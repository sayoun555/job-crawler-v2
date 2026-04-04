package com.portfolio.jobcrawler.infrastructure.crawler;

import com.portfolio.jobcrawler.infrastructure.crawler.core.PlaywrightScrapingEngine;
import com.portfolio.jobcrawler.infrastructure.crawler.dto.CrawledJobData;
import com.portfolio.jobcrawler.infrastructure.crawler.parser.JobAlioParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobAlioScraper implements JobScraper {

    private final PlaywrightScrapingEngine scrapingEngine;
    private final JobAlioParser parser;

    @Override
    public String getSiteName() {
        return parser.getSiteName();
    }

    @Override
    public List<CrawledJobData> scrapeJobs(String keyword, String jobCategory, int maxPages, String companyType) {
        log.info("[JobAlioScraper] 잡알리오 크롤링 시작 - maxPages: {}", maxPages <= 0 ? "무제한" : maxPages);
        return scrapingEngine.scrape(parser, keyword, jobCategory, maxPages, 30_000, companyType);
    }
}
