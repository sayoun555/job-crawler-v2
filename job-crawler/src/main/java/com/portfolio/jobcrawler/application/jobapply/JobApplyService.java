package com.portfolio.jobcrawler.application.jobapply;

import com.portfolio.jobcrawler.domain.jobapply.entity.JobApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 입사 지원 Application Service 인터페이스.
 */
public interface JobApplyService {

    /** 지원 서류 준비 (자소서/포트폴리오 생성 + 프로젝트 매칭) */
    JobApplication prepareApplication(Long userId, Long jobPostingId, Long templateId);

    /** 최종 지원 실행 (Playwright 로봇 제출) */
    JobApplication submitApplication(Long userId, Long applicationId);

    /** 수동 지원 완료 표시 (홈페이지 지원용) */
    JobApplication markAsManuallyApplied(Long userId, Long applicationId);

    /** 실패 지원 재시도 */
    JobApplication retryApplication(Long userId, Long applicationId);

    /** 내 지원 이력 조회 */
    Page<JobApplication> getMyApplications(Long userId, Pageable pageable);

    /** 지원 이력 상세 */
    JobApplication getApplication(Long userId, Long applicationId);

    /** 서류 수정 */
    JobApplication updateDocuments(Long userId, Long applicationId, String coverLetter, String portfolio);

    /** 프로젝트 다시 선택 → AI 재생성 */
    JobApplication regenerateWithProjects(Long userId, Long applicationId, String selectedProjectIds, Long templateId);
}
