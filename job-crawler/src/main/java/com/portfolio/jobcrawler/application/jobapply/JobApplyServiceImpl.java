package com.portfolio.jobcrawler.application.jobapply;

import com.portfolio.jobcrawler.application.ai.AiAutomationService;
import com.portfolio.jobcrawler.application.ai.AiTaskQueue;
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
    private final AiTaskQueue aiTaskQueue;

    @Override
    @Transactional
    public JobApplication prepareApplication(Long userId, Long jobPostingId, Long templateId) {
        // 기존 지원서가 있으면 반환 (이미 AI 콘텐츠가 있는 경우)
        var existing = jobApplicationRepository.findByUserIdAndJobPostingId(userId, jobPostingId);
        if (existing.isPresent()) {
            JobApplication ex = existing.get();
            // 자소서가 이미 있으면 기존 것 반환, 비어있으면 AI 재생성
            if (ex.getCoverLetter() != null && !ex.getCoverLetter().isBlank()) {
                return ex;
            }
            // 빈 지원서 → AI가 아직 생성 중이므로 그대로 반환
            return ex;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        JobPosting job = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new CustomException(ErrorCode.JOB_POSTING_NOT_FOUND));
        Template template = templateId != null
                ? templateRepository.findById(templateId).orElse(null)
                : null;

        // 프로젝트 매칭 (빠름)
        List<Project> matched = aiAutomationService.matchProjects(userId, jobPostingId);
        String matchedIds = matched.stream().map(p -> String.valueOf(p.getId()))
                .collect(Collectors.joining(","));

        // 빈 지원서 즉시 생성 (AI 콘텐츠는 비동기로 채움)
        JobApplication application = JobApplication.builder()
                .user(user).jobPosting(job)
                .coverLetter("").portfolioContent("")
                .template(template).matchedProjectIds(matchedIds)
                .build();
        JobApplication saved = jobApplicationRepository.save(application);

        // AI 자소서/포트폴리오 비동기 생성 → WebSocket으로 알림
        final Long appId = saved.getId();
        new Thread(() -> generateAiContentAsync(userId, jobPostingId, templateId, appId)).start();

        return saved;
    }

    @Override
    @Transactional
    public JobApplication prepareCustomApplication(Long userId, Long jobPostingId,
            String sectionsJson, String additionalRequest,
            String portfolioSectionsJson, String portfolioAdditionalRequest) {
        var existing = jobApplicationRepository.findByUserIdAndJobPostingId(userId, jobPostingId);
        if (existing.isPresent()) {
            JobApplication ex = existing.get();
            if (ex.getCoverLetterSections() != null && !ex.getCoverLetterSections().isBlank()) {
                return ex;
            }
            return ex;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        JobPosting job = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new CustomException(ErrorCode.JOB_POSTING_NOT_FOUND));

        List<Project> matched = aiAutomationService.matchProjects(userId, jobPostingId);
        String matchedIds = matched.stream().map(p -> String.valueOf(p.getId()))
                .collect(Collectors.joining(","));

        JobApplication application = JobApplication.builder()
                .user(user).jobPosting(job)
                .coverLetter("").portfolioContent("")
                .matchedProjectIds(matchedIds)
                .build();
        JobApplication saved = jobApplicationRepository.save(application);

        final Long appId = saved.getId();
        new Thread(() -> generateCustomAiContentAsync(
                userId, jobPostingId, sectionsJson, additionalRequest,
                portfolioSectionsJson, portfolioAdditionalRequest, appId)).start();

        return saved;
    }

    private void generateCustomAiContentAsync(Long userId, Long jobPostingId,
            String sectionsJson, String additionalRequest,
            String portfolioSectionsJson, String portfolioAdditionalRequest,
            Long applicationId) {
        try {
            // 기업 분석을 먼저 실행
            try {
                aiAutomationService.analyzeCompany(userId, jobPostingId);
            } catch (Exception e) {
                log.warn("[커스텀지원서] 기업 분석 선행 실패 (자소서 생성은 계속): {}", e.getMessage());
            }

            // 커스텀 자소서 생성 (문항별 JSON)
            String coverLetterResult = aiAutomationService.generateCustomCoverLetter(
                    userId, jobPostingId, sectionsJson, additionalRequest);

            // 자소서만 생성 (포트폴리오는 프로젝트 단위로 별도 관리)
            jobApplicationRepository.findById(applicationId).ifPresent(app -> {
                app.updateCoverLetterSections(coverLetterResult);
                String formattedCoverLetter = formatSectionsAsText(coverLetterResult);
                app.updateDocuments(formattedCoverLetter, app.getPortfolioContent());
                jobApplicationRepository.save(app);
            });

            aiTaskQueue.complete("app-" + applicationId, userId,
                    "{\"applicationId\":" + applicationId + ",\"type\":\"PREPARE_COMPLETE\"}");

            log.info("[커스텀지원서] 비동기 AI 생성 완료 - appId:{}", applicationId);
        } catch (Exception e) {
            aiTaskQueue.fail("app-" + applicationId, userId, "커스텀 자소서 생성 실패: " + e.getMessage());
            log.error("[커스텀지원서] 비동기 AI 생성 실패 - appId:{}: {}", applicationId, e.getMessage());
        }
    }

    /**
     * AI 자소서/포트폴리오를 비동기로 생성하고 JobApplication에 저장 + WebSocket 알림.
     */
    private void generateAiContentAsync(Long userId, Long jobPostingId, Long templateId, Long applicationId) {
        try {
            // 기업 분석을 먼저 실행
            try {
                aiAutomationService.analyzeCompany(userId, jobPostingId);
            } catch (Exception e) {
                log.warn("[지원서준비] 기업 분석 선행 실패 (자소서 생성은 계속): {}", e.getMessage());
            }

            // 자소서만 생성 (포트폴리오는 프로젝트 단위로 별도 관리)
            String coverLetter = aiAutomationService.generateCoverLetter(userId, jobPostingId, templateId);

            jobApplicationRepository.findById(applicationId).ifPresent(app -> {
                app.updateDocuments(coverLetter, app.getPortfolioContent());
                jobApplicationRepository.save(app);
            });

            aiTaskQueue.complete("app-" + applicationId, userId,
                    "{\"applicationId\":" + applicationId + ",\"type\":\"PREPARE_COMPLETE\"}");

            log.info("[지원서준비] 비동기 AI 자소서 생성 완료 - appId:{}", applicationId);
        } catch (Exception e) {
            aiTaskQueue.fail("app-" + applicationId, userId, "AI 생성 실패: " + e.getMessage());
            log.error("[지원서준비] 비동기 AI 생성 실패 - appId:{}: {}", applicationId, e.getMessage());
        }
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

            // Playwright 로봇 실행 (전략 패턴 - 사이트별 Provider 자동 라우팅)
            ApplyResult result = autoApplyRobot.submitApply(userId, siteName, app, attachments);

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
    public JobApplication updateMatchedProjects(Long userId, Long applicationId, String projectIds) {
        JobApplication app = getOwnedApplication(userId, applicationId);
        app.updateMatchedProjects(projectIds);
        return app;
    }

    @Override
    @Transactional
    public JobApplication regenerateWithProjects(Long userId, Long applicationId,
            String selectedProjectIds, Long templateId) {
        JobApplication app = getOwnedApplication(userId, applicationId);
        app.updateMatchedProjects(selectedProjectIds);

        List<Long> projectIdList = java.util.Arrays.stream(selectedProjectIds.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(Long::valueOf).toList();

        Long jobId = app.getJobPosting().getId();
        String coverLetter = aiAutomationService.generateCoverLetter(userId, jobId, templateId, projectIdList);
        app.updateDocuments(coverLetter, app.getPortfolioContent());

        if (templateId != null) {
            Template t = templateRepository.findById(templateId).orElse(null);
            if (t != null)
                app.changeTemplate(t);
        }
        return app;
    }

    /**
     * 커스텀 자소서 JSON 섹션을 사람이 읽을 수 있는 텍스트로 변환한다.
     * coverLetter 필드에 저장하여 단일 textarea 사이트에서도 사용 가능하도록.
     */
    private String formatSectionsAsText(String sectionsJson) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.List<java.util.Map<String, String>> sections = mapper.readValue(
                    sectionsJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            StringBuilder sb = new StringBuilder();
            for (java.util.Map<String, String> section : sections) {
                String title = section.getOrDefault("title", "");
                String content = section.getOrDefault("content", "");
                if (!title.isBlank()) {
                    sb.append("[").append(title).append("]\n");
                }
                sb.append(content).append("\n\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("[지원서준비] 섹션 텍스트 변환 실패, 원본 반환: {}", e.getMessage());
            return sectionsJson;
        }
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
