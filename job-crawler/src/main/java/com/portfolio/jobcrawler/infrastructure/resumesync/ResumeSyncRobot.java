package com.portfolio.jobcrawler.infrastructure.resumesync;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.portfolio.jobcrawler.domain.resume.entity.Resume;
import com.portfolio.jobcrawler.application.account.ExternalAccountService;
import com.portfolio.jobcrawler.domain.jobposting.vo.SourceSite;
import com.portfolio.jobcrawler.infrastructure.autoapply.AuthSessionManager;
import com.portfolio.jobcrawler.infrastructure.crawler.PlaywrightManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 이력서 동기화 오케스트레이터 (SRP, OCP 준수).
 * - 세션 관리는 AuthSessionManager에게 위임
 * - 사이트별 동기화 로직은 ResumeProvider(전략 패턴)에게 위임
 */
@Slf4j
@Component
public class ResumeSyncRobot {

    private final PlaywrightManager playwrightManager;
    private final AuthSessionManager sessionManager;
    private final ExternalAccountService externalAccountService;
    private final SaraminResumeImporter saraminResumeImporter;
    private final Map<String, ResumeProvider> providers;

    public ResumeSyncRobot(PlaywrightManager playwrightManager,
                           AuthSessionManager sessionManager,
                           ExternalAccountService externalAccountService,
                           SaraminResumeImporter saraminResumeImporter,
                           List<ResumeProvider> providerList) {
        this.playwrightManager = playwrightManager;
        this.sessionManager = sessionManager;
        this.externalAccountService = externalAccountService;
        this.saraminResumeImporter = saraminResumeImporter;
        this.providers = providerList.stream()
                .collect(Collectors.toMap(ResumeProvider::getSiteName, Function.identity()));
    }

    /**
     * 단일 사이트에 이력서를 동기화한다.
     */
    public ResumeSyncResult syncResume(Long userId, String site, Resume resume) {
        ResumeProvider provider = getProvider(site).orElse(null);
        if (provider == null) {
            return ResumeSyncResult.fail("지원하지 않는 플랫폼입니다: " + site);
        }

        if (!sessionManager.hasSession(userId, site)) {
            invalidateSessionSafely(userId, site);
            return ResumeSyncResult.sessionExpired(site + " 세션이 만료되었습니다. 설정에서 다시 연동해주세요.");
        }

        log.info("[ResumeSyncRobot] {} 이력서 동기화 시작 - userId:{}", site, userId);

        // headed 모드로 실행 (디버깅용 - 실제 브라우저 화면 표시)
        com.microsoft.playwright.Browser headedBrowser = null;
        try {
            headedBrowser = playwrightManager.launchHeadedBrowser();
            BrowserContext ctx = headedBrowser.newContext(
                    new com.microsoft.playwright.Browser.NewContextOptions()
                            .setViewportSize(1280, 900)
                            .setLocale("ko-KR")
                            .setTimezoneId("Asia/Seoul"));

            injectSessionCookies(ctx, userId, site);

            Page page = ctx.newPage();
            ResumeSyncResult result = provider.syncResume(page, playwrightManager, resume);

            if (result.isSessionExpired()) {
                log.warn("[ResumeSyncRobot] {} 세션 만료 감지 - 연동 해제 처리", site);
                invalidateSessionSafely(userId, site);
            }

            log.info("[ResumeSyncRobot] {} 동기화 완료 - 상태:{}", site, result.getStatus());

            // 디버깅: 저장 후 5초간 브라우저 유지
            try { Thread.sleep(5000); } catch (InterruptedException ignored) {}

            page.close();
            ctx.close();
            return result;

        } catch (Exception e) {
            log.error("[ResumeSyncRobot] {} 동기화 실패: {}", site, e.getMessage());
            return ResumeSyncResult.fail(e.getMessage());
        } finally {
            if (headedBrowser != null) {
                headedBrowser.close();
            }
        }
    }

    /**
     * 등록된 모든 사이트에 이력서를 순차 동기화한다.
     */
    public Map<String, ResumeSyncResult> syncToAllSites(Long userId, Resume resume) {
        Map<String, ResumeSyncResult> results = new LinkedHashMap<>();

        for (Map.Entry<String, ResumeProvider> entry : providers.entrySet()) {
            String site = entry.getKey();
            log.info("[ResumeSyncRobot] 전체 동기화 진행 중 - site:{}", site);
            results.put(site, syncResume(userId, site, resume));
        }

        return results;
    }

    /**
     * 외부 사이트에서 이력서를 가져온다 (Import).
     */
    public SaraminResumeImporter.ImportResult importResume(Long userId, String site, Resume resume) {
        if (!sessionManager.hasSession(userId, site)) {
            return SaraminResumeImporter.ImportResult.sessionExpired(site + " 세션이 만료되었습니다.");
        }

        log.info("[ResumeSyncRobot] {} 이력서 가져오기 시작 - userId:{}", site, userId);

        com.microsoft.playwright.Browser headedBrowser = null;
        try {
            headedBrowser = playwrightManager.launchHeadedBrowser();
            BrowserContext ctx = headedBrowser.newContext(
                    new com.microsoft.playwright.Browser.NewContextOptions()
                            .setViewportSize(1280, 900)
                            .setLocale("ko-KR")
                            .setTimezoneId("Asia/Seoul"));

            injectSessionCookies(ctx, userId, site);
            Page page = ctx.newPage();

            SaraminResumeImporter.ImportResult result =
                    saraminResumeImporter.importResume(page, playwrightManager, resume);

            if (result.sessionExpired()) {
                invalidateSessionSafely(userId, site);
            }

            log.info("[ResumeSyncRobot] {} 가져오기 완료 - 성공:{}, 항목:{}", site, result.success(), result.importedCount());

            page.close();
            ctx.close();
            return result;

        } catch (Exception e) {
            log.error("[ResumeSyncRobot] {} 가져오기 실패: {}", site, e.getMessage());
            return SaraminResumeImporter.ImportResult.fail(e.getMessage());
        } finally {
            if (headedBrowser != null) headedBrowser.close();
        }
    }

    private Optional<ResumeProvider> getProvider(String site) {
        return Optional.ofNullable(providers.get(site != null ? site.toUpperCase() : ""));
    }

    /**
     * 세션 만료 시 DB + Redis 세션을 안전하게 무효화한다.
     */
    private void invalidateSessionSafely(Long userId, String site) {
        try {
            SourceSite sourceSite = SourceSite.valueOf(site.toUpperCase());
            externalAccountService.invalidateSession(userId, sourceSite);
            log.info("[ResumeSyncRobot] {} 연동 해제 완료 (userId:{})", site, userId);
        } catch (Exception e) {
            log.error("[ResumeSyncRobot] {} 연동 해제 실패: {}", site, e.getMessage());
        }
    }

    /**
     * Redis에 저장된 세션 쿠키를 Playwright BrowserContext에 주입한다.
     */
    private void injectSessionCookies(BrowserContext ctx, Long userId, String site) {
        String cookiesJson = sessionManager.getSession(userId, site);
        if (cookiesJson == null) return;

        try {
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> cookieList = mapper.readValue(
                    cookiesJson, new TypeReference<List<Map<String, Object>>>() {});

            ctx.addCookies(cookieList.stream().map(map -> {
                com.microsoft.playwright.options.Cookie c = new com.microsoft.playwright.options.Cookie(
                        (String) map.get("name"), (String) map.get("value")
                );
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
            log.error("[ResumeSyncRobot] 쿠키 주입 실패: {}", e.getMessage());
        }
    }
}
