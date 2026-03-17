package com.portfolio.jobcrawler.infrastructure.resumesync;

import com.microsoft.playwright.Page;
import com.portfolio.jobcrawler.domain.resume.entity.Resume;
import com.portfolio.jobcrawler.infrastructure.crawler.PlaywrightManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 링커리어 이력서 동기화 구현체.
 * 실제 링커리어 HTML 구조 확인 후 각 섹션별 입력 로직을 구현한다.
 */
@Slf4j
@Component
public class LinkareerResumeProvider implements ResumeProvider {

    private static final String SITE_NAME = "LINKAREER";
    private static final String RESUME_WRITE_URL =
            "https://linkareer.com/resume/write";

    @Override
    public String getSiteName() {
        return SITE_NAME;
    }

    @Override
    public ResumeSyncResult syncResume(Page page, PlaywrightManager pm, Resume resume) {
        log.info("[LinkareerResume] 이력서 동기화 시작");

        try {
            page.navigate(RESUME_WRITE_URL,
                    new Page.NavigateOptions().setWaitUntil(
                            com.microsoft.playwright.options.WaitUntilState.LOAD));
            pm.shortDelay();

            String currentUrl = page.url();
            if (currentUrl.contains("/login") || currentUrl.contains("/signin")) {
                log.error("[LinkareerResume] 로그인 세션 만료: {}", currentUrl);
                return ResumeSyncResult.sessionExpired("링커리어 로그인 세션이 만료되었습니다. 설정에서 다시 연동해주세요.");
            }

            log.info("[LinkareerResume] 이력서 작성 페이지 로드 완료: {}", currentUrl);

            ResumeSyncResult.Builder result = ResumeSyncResult.builder();
            fillEducations(page, pm, resume, result);
            fillCareers(page, pm, resume, result);
            fillSkills(page, pm, resume, result);
            fillActivities(page, pm, resume, result);
            fillSelfIntroduction(page, pm, resume, result);
            saveResume(page, pm, result);

            return result.build();

        } catch (Exception e) {
            log.error("[LinkareerResume] 동기화 실패: {}", e.getMessage(), e);
            return ResumeSyncResult.fail(e.getMessage());
        }
    }

    private void fillEducations(Page page, PlaywrightManager pm, Resume resume,
                                ResumeSyncResult.Builder result) {
        // TODO: 링커리어 학력 섹션 HTML 확인 후 구현
        result.addSectionResult("학력", true, "스킵 (구현 예정)");
    }

    private void fillCareers(Page page, PlaywrightManager pm, Resume resume,
                             ResumeSyncResult.Builder result) {
        // TODO: 링커리어 경력 섹션 HTML 확인 후 구현
        result.addSectionResult("경력", true, "스킵 (구현 예정)");
    }

    private void fillSkills(Page page, PlaywrightManager pm, Resume resume,
                            ResumeSyncResult.Builder result) {
        // TODO: 링커리어 스킬 섹션 HTML 확인 후 구현
        result.addSectionResult("스킬", true, "스킵 (구현 예정)");
    }

    private void fillActivities(Page page, PlaywrightManager pm, Resume resume,
                                ResumeSyncResult.Builder result) {
        // TODO: 링커리어 경험/활동 섹션 HTML 확인 후 구현
        result.addSectionResult("경험/활동", true, "스킵 (구현 예정)");
    }

    private void fillSelfIntroduction(Page page, PlaywrightManager pm, Resume resume,
                                      ResumeSyncResult.Builder result) {
        // TODO: 링커리어 자기소개서 섹션 HTML 확인 후 구현
        result.addSectionResult("자기소개서", true, "스킵 (구현 예정)");
    }

    private void saveResume(Page page, PlaywrightManager pm, ResumeSyncResult.Builder result) {
        // TODO: 링커리어 최종저장 HTML 확인 후 구현
        result.addSectionResult("최종저장", true, "스킵 (구현 예정)");
    }
}
