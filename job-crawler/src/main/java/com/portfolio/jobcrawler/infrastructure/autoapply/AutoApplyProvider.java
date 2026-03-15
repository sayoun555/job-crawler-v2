package com.portfolio.jobcrawler.infrastructure.autoapply;

import com.microsoft.playwright.Page;
import com.portfolio.jobcrawler.domain.jobapply.entity.JobApplication;
import com.portfolio.jobcrawler.infrastructure.crawler.PlaywrightManager;

import java.nio.file.Path;
import java.util.List;

/**
 * 전사적 자동 지원 프로바이더 인터페이스 (OCP 준수).
 * 새로운 채용 플랫폼이 추가되면 이 인터페이스의 구현체만 추가하면 됨.
 */
public interface AutoApplyProvider {

    /**
     * 지원하는 채용 플랫폼 사이트를 반환
     */
    String getSiteName();

    /**
     * 해당 플랫폼에 로그인하여 페이지에 세션을 생성
     * (Headless 모드로 아이디/비밀번호 자동 입력 시 사용)
     * @return 로그인 성공 여부
     */
    boolean login(Page page, PlaywrightManager playwrightManager, String loginId, String password);

    /**
     * 해당 플랫폼에 실제로 이력서를 제출
     * @return 지원 결과 검증 (성공/실패 등)
     */
    ApplyResult submit(Page page, PlaywrightManager playwrightManager, JobApplication app, List<Path> attachments);
}
