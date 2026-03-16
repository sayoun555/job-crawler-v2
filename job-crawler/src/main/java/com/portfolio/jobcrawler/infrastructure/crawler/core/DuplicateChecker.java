package com.portfolio.jobcrawler.infrastructure.crawler.core;

/**
 * URL 기반 중복 공고 판별 인터페이스.
 * 엔진이 상세 페이지를 열기 전에 이미 크롤링된 URL인지 확인한다.
 */
public interface DuplicateChecker {

    boolean isDuplicate(String url, String sourceSite);
}
