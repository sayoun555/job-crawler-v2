package com.portfolio.jobcrawler.infrastructure.resumesync;

import com.microsoft.playwright.Page;
import com.portfolio.jobcrawler.domain.resume.entity.Resume;
import com.portfolio.jobcrawler.infrastructure.crawler.PlaywrightManager;

/**
 * 사이트별 이력서 동기화 전략 인터페이스 (OCP 준수).
 * 새로운 채용 사이트 추가 시 이 인터페이스를 구현하면 된다.
 */
public interface ResumeProvider {

    String getSiteName();

    ResumeSyncResult syncResume(Page page, PlaywrightManager playwrightManager, Resume resume);
}
