package com.portfolio.jobcrawler.infrastructure.autoapply;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.portfolio.jobcrawler.domain.jobapply.entity.JobApplication;
import com.portfolio.jobcrawler.infrastructure.crawler.PlaywrightManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 자동 지원 Playwright 로봇 (SRP, OCP 준수 리팩토링)
 * - 세션 관리는 `AuthSessionManager`에게 위임 (조합)
 * - 사이트별 구체적인 로직은 `AutoApplyProvider` (전략 패턴)에게 위임
 */
@Slf4j
@Component
public class AutoApplyRobot {

    private final PlaywrightManager playwrightManager;
    private final AuthSessionManager sessionManager;
    private final Map<String, AutoApplyProvider> providers;

    public AutoApplyRobot(PlaywrightManager playwrightManager,
                          AuthSessionManager sessionManager,
                          List<AutoApplyProvider> providerList) {
        this.playwrightManager = playwrightManager;
        this.sessionManager = sessionManager;
        // 사이트 이름(String)을 키로 하여 Provider 매핑
        this.providers = providerList.stream()
                .collect(Collectors.toMap(AutoApplyProvider::getSiteName, Function.identity()));
    }

    /**
     * 지원하는 사이트의 Provider 반환
     */
    private Optional<AutoApplyProvider> getProvider(String site) {
        return Optional.ofNullable(providers.get(site != null ? site.toUpperCase() : ""));
    }

    /**
     * 대리 로그인하여 세션(쿠키)을 Redis에 저장한다.
     */
    public boolean loginAndSaveSession(Long userId, String site, String loginId, String password) {
        AutoApplyProvider provider = getProvider(site).orElse(null);
        if (provider == null) {
            log.error("[AutoApplyRobot] 미지원 사이트: {}", site);
            return false;
        }

        log.info("[AutoApplyRobot] 대리 로그인 시도 - userId:{}, site:{}", userId, site);

        try (BrowserContext ctx = playwrightManager.createStealthContext()) {
            Page page = ctx.newPage();
            boolean isSuccess = provider.login(page, playwrightManager, loginId, password);

            if (isSuccess) {
                sessionManager.saveSessionFromContext(userId, site, ctx);
            }
            
            page.close();
            return isSuccess;
        } catch (Exception e) {
            log.error("[AutoApplyRobot] 로그인 실패: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Playwright 팝업 브라우저를 띄워 유저가 직접 로그인하도록 한다. (소셜 로그인 등)
     */
    public String openLoginPopup(Long userId, String site) {
        log.info("[AutoApplyRobot] 팝업 로그인 시작 - userId:{}, site:{}", userId, site);

        String loginUrl;
        if ("SARAMIN".equalsIgnoreCase(site)) {
            loginUrl = "https://www.saramin.co.kr/zf_user/auth";
        } else if ("JOBPLANET".equalsIgnoreCase(site)) {
            loginUrl = "https://www.jobplanet.co.kr/users/sign_in";
        } else {
            return null;
        }

        Browser headedBrowser = null;
        try {
            headedBrowser = playwrightManager.launchHeadedBrowser();
            BrowserContext ctx = headedBrowser.newContext(
                    new Browser.NewContextOptions()
                            .setViewportSize(1280, 800)
                            .setLocale("ko-KR")
                            .setTimezoneId("Asia/Seoul"));

            Page page = ctx.newPage();
            page.navigate(loginUrl);

            log.info("[AutoApplyRobot] 유저 로그인 대기 중... (최대 120초)");
            page.waitForURL(
                    url -> !url.contains("/auth") && !url.contains("/sign_in") && !url.contains("/login"),
                    new Page.WaitForURLOptions().setTimeout(120_000));

            log.info("[AutoApplyRobot] 로그인 감지! URL: {}", page.url());

            String cookiesJson = sessionManager.saveSessionFromContext(userId, site, ctx);
            log.info("[AutoApplyRobot] {} 팝업 로그인 성공, 세션 저장 완료", site);

            page.close();
            ctx.close();
            return cookiesJson;

        } catch (Exception e) {
            log.error("[AutoApplyRobot] 팝업 로그인 실패: {}", e.getMessage());
            return null;
        } finally {
            if (headedBrowser != null) {
                headedBrowser.close();
            }
        }
    }

    /**
     * 자동 지원 제출 (전략 패턴 활용)
     */
    public ApplyResult submitApply(Long userId, String site, JobApplication app, List<Path> attachments) {
        AutoApplyProvider provider = getProvider(site).orElse(null);
        if (provider == null) {
            return ApplyResult.fail("지원하지 않는 플랫폼입니다: " + site);
        }

        if (!sessionManager.hasSession(userId, site)) {
            return ApplyResult.fail(site + " 세션이 만료되었습니다. 재로그인이 필요합니다.");
        }

        log.info("[AutoApplyRobot] {} 자동 지원 위임 - 공고: {}", site, app.getJobPosting().getTitle());

        try (BrowserContext ctx = playwrightManager.createStealthContext()) {
            // Redis에서 쿠키 복원은 다음 로직에서 Playwright Context에 Inject 하도록 수정 필요 시 처리
            // 현재 PlaywrightContext에 수동으로 쿠키를 넣으려면 ObjectMapper 역직렬화 필요
            injectSessionCookies(ctx, userId, site);

            Page page = ctx.newPage();
            return provider.submit(page, playwrightManager, app, attachments);

        } catch (Exception e) {
            log.error("[AutoApplyRobot] {} 지원 실패: {}", site, e.getMessage());
            return ApplyResult.fail(e.getMessage());
        }
    }

    /**
     * 구형 submitSaramin 메서드 (호환성 유지)
     */
    public ApplyResult submitSaramin(Long userId, JobApplication app, List<Path> attachments) {
        return submitApply(userId, "SARAMIN", app, attachments);
    }

    /**
     * 구형 submitJobPlanet 메서드 (호환성 유지)
     */
    public ApplyResult submitJobPlanet(Long userId, JobApplication app, List<Path> attachments) {
        return submitApply(userId, "JOBPLANET", app, attachments);
    }

    private void injectSessionCookies(BrowserContext ctx, Long userId, String site) {
        String cookiesJson = sessionManager.getSession(userId, site);
        if (cookiesJson == null) return;
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> cookieList = mapper.readValue(cookiesJson, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
            
            // Playwright의 Cookie 객체로 변환하여 addCookies
            // 이 프로젝트에서는 Json 데이터를 단순 저장만 해왔으므로, Playwright 호환 객체로 재생성
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
            log.error("[AutoApplyRobot] 쿠키 주입 실패: {}", e.getMessage());
        }
    }
}
