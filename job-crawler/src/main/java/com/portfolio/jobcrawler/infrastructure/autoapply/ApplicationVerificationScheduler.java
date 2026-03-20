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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 2단계 사후 교차검증 스케줄러 (Step 8.5).
 * 매 6시간마다 각 사이트의 "내 지원 현황" 페이지를 크롤링하여
 * DB의 APPLIED 항목과 대조 → VERIFIED 또는 FAILED로 업데이트.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationVerificationScheduler {

    private final JobApplicationRepository jobApplicationRepository;
    private final PlaywrightManager playwrightManager;
    private final AuthSessionManager sessionManager;

    @Scheduled(cron = "0 0 */6 * * *")
    @Transactional
    public void verifyApplications() {
        log.info("[검증] 사후 교차검증 시작");

        List<JobApplication> appliedJobs = jobApplicationRepository.findByStatus(ApplicationStatus.APPLIED);
        if (appliedJobs.isEmpty()) {
            log.info("[검증] 검증 대상 없음");
            return;
        }

        appliedJobs.stream()
                .collect(Collectors.groupingBy(app -> app.getUser().getId()))
                .forEach((userId, apps) -> {
                    try {
                        verifyForUser(userId, apps, SourceSite.SARAMIN);
                        verifyForUser(userId, apps, SourceSite.JOBKOREA);
                        verifyForUser(userId, apps, SourceSite.JOBPLANET);
                        verifyForUser(userId, apps, SourceSite.LINKAREER);
                    } catch (Exception e) {
                        log.error("[검증] userId:{} 검증 실패: {}", userId, e.getMessage());
                    }
                });

        log.info("[검증] 사후 교차검증 완료");
    }

    private void verifyForUser(Long userId, List<JobApplication> allApps, SourceSite site) {
        if (!sessionManager.hasSession(userId, site.name())) {
            return;
        }

        List<JobApplication> siteApps = allApps.stream()
                .filter(a -> a.getJobPosting().getSource() == site)
                .toList();
        if (siteApps.isEmpty()) {
            return;
        }

        String applicationPageUrl = getApplicationPageUrl(site);
        if (applicationPageUrl == null) {
            return;
        }

        try (BrowserContext ctx = playwrightManager.createStealthContext()) {
            injectSession(ctx, userId, site.name());

            Page page = ctx.newPage();
            page.navigate(applicationPageUrl);
            playwrightManager.longDelay();

            List<String> appliedTitles = extractAppliedTitles(page, site);

            for (JobApplication app : siteApps) {
                String jobTitle = app.getJobPosting().getTitle();
                boolean found = appliedTitles.stream()
                        .anyMatch(t -> t.contains(jobTitle) || jobTitle.contains(t));
                if (found) {
                    app.verify();
                    log.info("[검증] VERIFIED ({}): {}", site, jobTitle);
                } else {
                    app.markAsFailed("사후검증 실패 - " + site.name() + " 지원 현황에서 미확인");
                    log.warn("[검증] FAILED ({}): {}", site, jobTitle);
                }
            }
            page.close();
        } catch (Exception e) {
            log.error("[검증] {} 검증 에러: {}", site, e.getMessage());
        }
    }

    private String getApplicationPageUrl(SourceSite site) {
        return switch (site) {
            case SARAMIN -> "https://www.saramin.co.kr/zf_user/apply-manage/status";
            case JOBKOREA -> "https://www.jobkorea.co.kr/User/Appl/Main";
            case JOBPLANET -> "https://www.jobplanet.co.kr/users/job_applications";
            case LINKAREER -> "https://linkareer.com/my-career/applications";
        };
    }

    private List<String> extractAppliedTitles(Page page, SourceSite site) {
        String selector = switch (site) {
            case SARAMIN -> ".apply_list .tit, .list_applied .title, .tit_job a";
            case JOBKOREA -> ".tit_job a, .list-post .tit, [class*=apply] .title";
            case JOBPLANET -> "[class*=application] h4, [class*=apply] .title, .job-title";
            case LINKAREER -> "[class*=application] h4, [class*=apply] p, .activity-title";
        };

        Locator appliedItems = page.locator(selector);
        List<String> titles = new ArrayList<>();
        for (int i = 0; i < appliedItems.count(); i++) {
            String text = appliedItems.nth(i).textContent();
            if (text != null && !text.isBlank()) {
                titles.add(text.trim());
            }
        }
        log.info("[검증] {} 지원 현황에서 {} 건 발견", site, titles.size());
        return titles;
    }

    private void injectSession(BrowserContext ctx, Long userId, String site) {
        String cookiesJson = sessionManager.getSession(userId, site);
        if (cookiesJson == null) return;

        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<java.util.Map<String, Object>> cookieList = mapper.readValue(cookiesJson,
                    new com.fasterxml.jackson.core.type.TypeReference<>() {});

            ctx.addCookies(cookieList.stream().map(map -> {
                var c = new com.microsoft.playwright.options.Cookie(
                        (String) map.get("name"), (String) map.get("value"));
                c.setDomain((String) map.get("domain"));
                c.setPath((String) map.get("path"));
                if (map.containsKey("secure")) c.setSecure((Boolean) map.get("secure"));
                if (map.containsKey("httpOnly")) c.setHttpOnly((Boolean) map.get("httpOnly"));
                Object expiresObj = map.get("expires");
                if (expiresObj instanceof Number) {
                    c.setExpires(((Number) expiresObj).doubleValue());
                }
                return c;
            }).collect(Collectors.toList()));
        } catch (Exception e) {
            log.error("[검증] {} 쿠키 주입 실패: {}", site, e.getMessage());
        }
    }
}
