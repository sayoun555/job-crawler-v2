package com.portfolio.jobcrawler.infrastructure.crawler.parser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.portfolio.jobcrawler.infrastructure.crawler.dto.CrawledJobData;

/**
 * 특정 웹사이트의 구조(DOM)를 이해하고 데이터를 파싱하는 책임만 가지는 도메인 객체 인터페이스
 * (브라우저나 페이지의 라이프사이클 관리는 관여하지 않음 - SRP, OCP 준수)
 */
public interface SiteParser {

    /**
     * 대상 사이트 이름 (예: SARAMIN, JOBPLANET)
     */
    String getSiteName();

    /**
     * 해당 사이트의 검색 URL 생성 규칙
     * @param keyword 검색 키워드 (예: "카카오")
     * @param jobCategory 직무 카테고리 (예: "서버/백엔드 개발")
     */
    String buildSearchUrl(String keyword, String jobCategory);

    /**
     * 검색 목록 페이지에서 공고 리스트 아이템들의 Locator 반환
     */
    Locator getListItems(Page page);

    /**
     * 목록 페이지가 로드될 때까지 대기하는 로직
     */
    void waitForListLoaded(Page page);

    /**
     * 리스트 아이템으로부터 개별 공고 데이터 추출
     * @param requestedJobCategory 크롤링 요청 시 지정된 직무 카테고리 (없으면 null)
     */
    CrawledJobData parseJobData(Page listPage, Locator item, String requestedJobCategory);

    /**
     * 다음 페이지로 넘어가는 동작 수행
     * @return 다음 페이지 이동 성공 여부 (더 이상 없으면 false)
     */
    boolean goToNextPage(Page page, int currentPageNum);

    // === 2단계 크롤링 지원 (리스트 파싱 → 병렬 상세 페이지 보강) ===

    /**
     * 2단계 크롤링 지원 여부.
     * true를 반환하면 엔진이 parseListData → enrichFromDetailPage 순서로 호출한다.
     */
    default boolean supportsTwoPhase() {
        return false;
    }

    /**
     * 리스트 아이템에서 상세 페이지 URL 추출 (상세 페이지를 열지 않음)
     */
    default String extractDetailUrl(Page listPage, Locator item) {
        return null;
    }

    /**
     * 리스트 아이템에서 기본 데이터만 파싱 (상세 페이지를 열지 않음)
     */
    default CrawledJobData parseListData(Page listPage, Locator item, String requestedJobCategory) {
        return parseJobData(listPage, item, requestedJobCategory);
    }

    /**
     * 이미 열려있는 상세 페이지로부터 CrawledJobData를 보강한다.
     * 엔진이 페이지의 생성·소멸을 관리하므로 파서는 파싱에만 집중한다.
     */
    default void enrichFromDetailPage(Page detailPage, CrawledJobData data) {
        // no-op
    }
}
