package com.portfolio.jobcrawler.infrastructure.crawler;

import com.portfolio.jobcrawler.infrastructure.crawler.dto.CrawledJobData;

import java.util.List;

/**
 * 크롤러 인터페이스 (Strategy Pattern).
 * 사이트별 Scraper가 구현.
 */
public interface JobScraper {

    /** 해당 크롤러가 담당하는 사이트 식별자 */
    String getSiteName();

    /** 
     * 키워드로 채용 공고 수집 
     * @param keyword 검색어
     * @param jobCategory 직무 카테고리
     * @param maxPages 최대 수집 페이지 수
     */
    List<CrawledJobData> scrapeJobs(String keyword, String jobCategory, int maxPages);
}
