package com.portfolio.jobcrawler.application.jobapply;

import com.portfolio.jobcrawler.application.ai.AiAutomationService;
import com.portfolio.jobcrawler.domain.account.entity.ExternalAccount;
import com.portfolio.jobcrawler.domain.account.repository.ExternalAccountRepository;
import com.portfolio.jobcrawler.domain.jobapply.entity.JobApplication;
import com.portfolio.jobcrawler.domain.jobapply.repository.JobApplicationRepository;
import com.portfolio.jobcrawler.domain.jobposting.entity.JobPosting;
import com.portfolio.jobcrawler.domain.jobposting.repository.JobPostingRepository;
import com.portfolio.jobcrawler.domain.jobposting.vo.SourceSite;
import com.portfolio.jobcrawler.domain.project.entity.Project;
import com.portfolio.jobcrawler.domain.template.entity.Template;
import com.portfolio.jobcrawler.domain.template.repository.TemplateRepository;
import com.portfolio.jobcrawler.domain.user.entity.User;
import com.portfolio.jobcrawler.domain.user.repository.UserRepository;
import com.portfolio.jobcrawler.global.error.CustomException;
import com.portfolio.jobcrawler.global.error.ErrorCode;
import com.portfolio.jobcrawler.infrastructure.autoapply.ApplyResult;
import com.portfolio.jobcrawler.infrastructure.autoapply.AutoApplyRobot;
import com.portfolio.jobcrawler.infrastructure.notification.DiscordNotificationSender;
import com.portfolio.jobcrawler.infrastructure.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobApplyServiceImpl implements JobApplyService {

    private final JobApplicationRepository jobApplicationRepository;
    private final JobPostingRepository jobPostingRepository;
    private final UserRepository userRepository;
    private final TemplateRepository templateRepository;
    private final AiAutomationService aiAutomationService;
    private final AutoApplyRobot autoApplyRobot;
    private final ExternalAccountRepository externalAccountRepository;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional
    public JobApplication prepareApplication(Long userId, Long jobPostingId, Long templateId) {
        if (jobApplicationRepository.existsByUserIdAndJobPostingId(userId, jobPostingId)) {
            throw new CustomException(ErrorCode.ALREADY_APPLIED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        JobPosting job = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new CustomException(ErrorCode.JOB_POSTING_NOT_FOUND));
        Template template = templateId != null
                ? templateRepository.findById(templateId).orElse(null)
                : null;

        // AI 자동 생성
        String coverLetter = aiAutomationService.generateCoverLetter(userId, jobPostingId, templateId);
        String portfolio = aiAutomationService.generatePortfolio(userId, jobPostingId, templateId);
        List<Project> matched = aiAutomationService.matchProjects(userId, jobPostingId);
        String matchedIds = matched.stream().map(p -> String.valueOf(p.getId()))
                .collect(Collectors.joining(","));

        JobApplication application = JobApplication.builder()
                .user(user).jobPosting(job)
                .coverLetter(coverLetter).portfolioContent(portfolio)
                .template(template).matchedProjectIds(matchedIds)
                .build();

        return jobApplicationRepository.save(application);
    }

    /**
     * 자동 지원 실행 (Step 8.4).
     * - 즉시지원(DIRECT_APPLY) → Playwright 로봇으로 폼 자동 제출
     * - 홈페이지 지원(HOMEPAGE) → URL만 반환 (프론트에서 새 탭)
     */
    @Override
    @Transactional
    public JobApplication submitApplication(Long userId, Long applicationId) {
        JobApplication app = getOwnedApplication(userId, applicationId);
        JobPosting job = app.getJobPosting();

        if (job.isDirectApply()) {
            // 즉시지원 → Playwright 로봇으로 자동 제출
            SourceSite site = job.getSource();
            String siteName = site.name();

            // 사용자의 외부 계정으로 세션 확보
            ExternalAccount account = externalAccountRepository
                    .findByUserIdAndSite(userId, site)
                    .orElseThrow(() -> new CustomException(ErrorCode.EXTERNAL_ACCOUNT_NOT_FOUND));

            // 첨부 파일 경로 수집
            List<Path> attachments = new ArrayList<>();
            // TODO: 사용자가 업로드한 파일 경로를 JobApplication에서 가져오기

            // Playwright 로봇 실행
            ApplyResult result;
            if ("SARAMIN".equals(siteName)) {
                result = autoApplyRobot.submitSaramin(userId, app, attachments);
            } else {
                result = autoApplyRobot.submitJobPlanet(userId, app, attachments);
            }

            // 1단계 즉시 검증 결과 반영
            if (result.isSuccess()) {
                app.markAsApplied();
                log.info("[AutoApply] 지원 성공: {}", job.getTitle());
            } else if (result.isFailed()) {
                app.markAsFailed(result.getMessage());
                log.warn("[AutoApply] 지원 실패: {} - {}", job.getTitle(), result.getMessage());
            } else {
                // UNKNOWN → APPLIED로 우선 표시, 사후검증 스케줄러가 교차확인
                app.markAsApplied();
                log.info("[AutoApply] 지원 결과 미확인, 사후검증 대기: {}", job.getTitle());
            }
        } else {
            // 홈페이지 지원 → 프론트에서 새 탭으로 열도록 PENDING 유지
            // 사용자가 직접 "수동 지원 완료" 버튼을 누르게 됨
            app.markAsApplied();
        }
        return app;
    }

    @Override
    @Transactional
    public JobApplication markAsManuallyApplied(Long userId, Long applicationId) {
        JobApplication app = getOwnedApplication(userId, applicationId);
        app.markAsManuallyApplied();
        return app;
    }

    @Override
    @Transactional
    public JobApplication retryApplication(Long userId, Long applicationId) {
        JobApplication app = getOwnedApplication(userId, applicationId);
        app.retry();
        return app;
    }

    @Override
    public Page<JobApplication> getMyApplications(Long userId, Pageable pageable) {
        return jobApplicationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Override
    public JobApplication getApplication(Long userId, Long applicationId) {
        return getOwnedApplication(userId, applicationId);
    }

    @Override
    @Transactional
    public JobApplication updateDocuments(Long userId, Long applicationId,
            String coverLetter, String portfolio) {
        JobApplication app = getOwnedApplication(userId, applicationId);
        app.updateDocuments(coverLetter, portfolio);
        return app;
    }

    @Override
    @Transactional
    public JobApplication regenerateWithProjects(Long userId, Long applicationId,
            String selectedProjectIds, Long templateId) {
        JobApplication app = getOwnedApplication(userId, applicationId);
        app.updateMatchedProjects(selectedProjectIds);

        Long jobId = app.getJobPosting().getId();
        String coverLetter = aiAutomationService.generateCoverLetter(userId, jobId, templateId);
        String portfolio = aiAutomationService.generatePortfolio(userId, jobId, templateId);
        app.updateDocuments(coverLetter, portfolio);

        if (templateId != null) {
            Template t = templateRepository.findById(templateId).orElse(null);
            if (t != null)
                app.changeTemplate(t);
        }
        return app;
    }

    private JobApplication getOwnedApplication(Long userId, Long applicationId) {
        JobApplication app = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));
        if (!app.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.APPLICATION_ACCESS_DENIED);
        }
        return app;
    }
}
