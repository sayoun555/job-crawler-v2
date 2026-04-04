package com.portfolio.jobcrawler.application.crawler;

/**
 * 크롤러 Application Service 인터페이스.
 */
public interface CrawlerService {
    int crawlAll(String keyword, String jobCategory, int maxPages, String companyType);
    int crawlBySite(String siteName, String keyword, String jobCategory, int maxPages, String companyType);
    int crawlBySites(java.util.List<String> siteNames, String keyword, String jobCategory, int maxPages, String companyType);
}
