package com.portfolio.jobcrawler.infrastructure.resumesync;

import com.microsoft.playwright.Page;
import com.portfolio.jobcrawler.application.ai.dto.CoverLetterSection;
import com.portfolio.jobcrawler.domain.resume.entity.Resume;
import com.portfolio.jobcrawler.infrastructure.crawler.PlaywrightManager;

import java.util.List;

/**
 * 사이트별 이력서 동기화 전략 인터페이스 (OCP 준수).
 * 새로운 채용 사이트 추가 시 이 인터페이스를 구현하면 된다.
 */
public interface ResumeProvider {

    String getSiteName();

    ResumeSyncResult syncResume(Page page, PlaywrightManager playwrightManager, Resume resume);

    /**
     * 이력서의 자기소개서 섹션을 커스텀 자소서 문항으로 업데이트한다.
     * 자동 지원 전에 호출하여 해당 공고에 맞는 자소서를 이력서에 반영.
     */
    ResumeSyncResult updateSelfIntroduction(Page page, PlaywrightManager playwrightManager,
                                            List<CoverLetterSection> sections);
}
