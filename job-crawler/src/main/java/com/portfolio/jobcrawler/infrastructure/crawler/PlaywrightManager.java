package com.portfolio.jobcrawler.infrastructure.crawler;

import com.microsoft.playwright.*;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Random;

@Slf4j
public class PlaywrightManager {

    private Playwright playwright;
    private Browser browser;
    private final Random random = new Random();

    private static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15");

    public void init() {
        log.info("Playwright 초기화...");
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(false)
                        .setArgs(List.of(
                                "--disable-blink-features=AutomationControlled",
                                "--no-sandbox", "--disable-dev-shm-usage",
                                "--disable-infobars", "--disable-extensions", "--disable-gpu")));
    }

    public BrowserContext createStealthContext() {
        String ua = USER_AGENTS.get(random.nextInt(USER_AGENTS.size()));
        BrowserContext ctx = browser.newContext(
                new Browser.NewContextOptions()
                        .setUserAgent(ua)
                        .setViewportSize(1280 + random.nextInt(640), 720 + random.nextInt(360))
                        .setLocale("ko-KR")
                        .setTimezoneId("Asia/Seoul"));
        ctx.addInitScript("""
                Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
                Object.defineProperty(navigator, 'plugins', { get: () => [1,2,3,4,5] });
                Object.defineProperty(navigator, 'languages', { get: () => ['ko-KR','ko','en-US','en'] });
                window.chrome = { runtime: {} };
                """);
        return ctx;
    }

    /**
     * 유저가 직접 로그인할 수 있는 visible(headed) 브라우저를 띄운다.
     * 소셜 로그인(카카오/네이버/구글) 대응용.
     */
    public Browser launchHeadedBrowser() {
        log.info("Headed 브라우저 실행 (소셜 로그인 팝업용)...");
        return playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(false)
                        .setArgs(List.of(
                                "--disable-blink-features=AutomationControlled",
                                "--no-sandbox", "--disable-infobars")));
    }

    public Page createPage() {
        return createStealthContext().newPage();
    }

    public void randomDelay(int minMs, int maxMs) {
        try {
            Thread.sleep(minMs + random.nextInt(maxMs - minMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void shortDelay() {
        randomDelay(2000, 5000);
    }

    public void longDelay() {
        randomDelay(5000, 10000);
    }

    public void close() {
        if (browser != null)
            browser.close();
        if (playwright != null)
            playwright.close();
    }
}
