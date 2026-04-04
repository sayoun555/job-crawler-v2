package com.portfolio.jobcrawler.infrastructure.crawler;

import com.portfolio.jobcrawler.infrastructure.crawler.core.PlaywrightScrapingEngine;
import com.portfolio.jobcrawler.infrastructure.crawler.dto.CrawledJobData;
import com.portfolio.jobcrawler.infrastructure.crawler.parser.SaraminParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

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
    public List<CrawledJobData> scrapeJobs(String keyword, String jobCategory, int maxPages, String companyType) {
        log.info("[SaraminScraper] 사람인 크롤링 시작 위임 - maxPages: {}, companyType: {}", maxPages <= 0 ? "무제한" : maxPages, companyType);
        return scrapingEngine.scrape(parser, keyword, jobCategory, maxPages, 60_000, companyType);
    }
}
