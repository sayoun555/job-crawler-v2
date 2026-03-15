package com.portfolio.jobcrawler.infrastructure.autoapply;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.portfolio.jobcrawler.domain.jobapply.entity.JobApplication;
import com.portfolio.jobcrawler.domain.jobapply.repository.JobApplicationRepository;
import com.portfolio.jobcrawler.domain.jobposting.vo.ApplicationStatus;
import com.portfolio.jobcrawler.domain.jobposting.vo.SourceSite;
import com.portfolio.jobcrawler.infrastructure.crawler.PlaywrightManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 2단계 사후 교차검증 스케줄러 (Step 8.5).
 * 매 6시간마다 사람인/잡플래닛 "내 지원 현황" 페이지를 크롤링하여
 * DB의 APPLIED 항목과 대조 → VERIFIED 또는 FAILED로 업데이트.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationVerificationScheduler {

    private final JobApplicationRepository jobApplicationRepository;
    private final PlaywrightManager playwrightManager;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(cron = "0 0 */6 * * *")
    @Transactional
    public void verifyApplications() {
        log.info("[검증] 사후 교차검증 시작");

        List<JobApplication> appliedJobs = jobApplicationRepository.findByStatus(ApplicationStatus.APPLIED);
        if (appliedJobs.isEmpty()) {
            log.info("[검증] 검증 대상 없음");
            return;
        }

        // 사용자별 그룹핑
        appliedJobs.stream()
                .collect(Collectors.groupingBy(app -> app.getUser().getId()))
                .forEach((userId, apps) -> {
                    try {
                        verifySaraminForUser(userId, apps);
                        verifyJobPlanetForUser(userId, apps);
                    } catch (Exception e) {
                        log.error("[검증] userId:{} 검증 실패: {}", userId, e.getMessage());
                    }
                });

        log.info("[검증] 사후 교차검증 완료");
    }

    private void verifySaraminForUser(Long userId, List<JobApplication> apps) {
        String sessionData = redisTemplate.opsForValue().get("session:" + userId + ":SARAMIN");
        if (sessionData == null)
            return;

        List<JobApplication> saraminApps = apps.stream()
                .filter(a -> a.getJobPosting().getSource() == SourceSite.SARAMIN)
                .toList();
        if (saraminApps.isEmpty())
            return;

        try (BrowserContext ctx = playwrightManager.createStealthContext()) {
            Page page = ctx.newPage();
            page.navigate("https://www.saramin.co.kr/zf_user/apply-manage/status");
            playwrightManager.longDelay();

            Locator appliedItems = page.locator(".apply_list .tit, .list_applied .title");
            List<String> appliedTitles = new ArrayList<>();
            for (int i = 0; i < appliedItems.count(); i++) {
                String text = appliedItems.nth(i).textContent();
                if (text != null)
                    appliedTitles.add(text.trim());
            }

            for (JobApplication app : saraminApps) {
                String jobTitle = app.getJobPosting().getTitle();
                boolean found = appliedTitles.stream()
                        .anyMatch(t -> t.contains(jobTitle) || jobTitle.contains(t));
                if (found) {
                    app.verify();
                    log.info("[검증] VERIFIED: {}", jobTitle);
                } else {
                    app.markAsFailed("사후검증 실패 - 사람인 지원 현황에서 미확인");
                    log.warn("[검증] FAILED: {}", jobTitle);
                }
            }
            page.close();
        } catch (Exception e) {
            log.error("[검증] 사람인 검증 에러: {}", e.getMessage());
        }
    }

    private void verifyJobPlanetForUser(Long userId, List<JobApplication> apps) {
        String sessionData = redisTemplate.opsForValue().get("session:" + userId + ":JOBPLANET");
        if (sessionData == null)
            return;

        List<JobApplication> jpApps = apps.stream()
                .filter(a -> a.getJobPosting().getSource() == SourceSite.JOBPLANET)
                .toList();
        if (jpApps.isEmpty())
            return;

        try (BrowserContext ctx = playwrightManager.createStealthContext()) {
            Page page = ctx.newPage();
            page.navigate("https://www.jobplanet.co.kr/users/job_applications");
            playwrightManager.longDelay();

            Locator appliedItems = page.locator("[class*=application] h4, [class*=apply] .title");
            List<String> appliedTitles = new ArrayList<>();
            for (int i = 0; i < appliedItems.count(); i++) {
                String text = appliedItems.nth(i).textContent();
                if (text != null)
                    appliedTitles.add(text.trim());
            }

            for (JobApplication app : jpApps) {
                String jobTitle = app.getJobPosting().getTitle();
                boolean found = appliedTitles.stream()
                        .anyMatch(t -> t.contains(jobTitle) || jobTitle.contains(t));
                if (found) {
                    app.verify();
                } else {
                    app.markAsFailed("사후검증 실패 - 잡플래닛 지원 현황에서 미확인");
                }
            }
            page.close();
        } catch (Exception e) {
            log.error("[검증] 잡플래닛 검증 에러: {}", e.getMessage());
        }
    }
}
