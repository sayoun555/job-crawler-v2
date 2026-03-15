package com.portfolio.jobcrawler.infrastructure.autoapply;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.portfolio.jobcrawler.domain.jobapply.entity.JobApplication;
import com.portfolio.jobcrawler.infrastructure.crawler.PlaywrightManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * 잡플래닛용 자동 지원 프로바이더 구현체
 */
@Slf4j
@Component
public class JobPlanetApplyProvider implements AutoApplyProvider {

    @Override
    public String getSiteName() {
        return "JOBPLANET";
    }

    @Override
    public boolean login(Page page, PlaywrightManager playwrightManager, String loginId, String password) {
        log.info("[JobPlanetApplyProvider] 잡플래닛 로그인 시도 - {}", loginId);
        page.navigate("https://www.jobplanet.co.kr/users/sign_in");
        playwrightManager.shortDelay();

        Locator emailInput = page.locator("input[name='user[email]'], #user_email");
        Locator pwdInput = page.locator("input[name='user[password]'], #user_password");
        
        if (emailInput.count() == 0 || pwdInput.count() == 0) {
            log.warn("[JobPlanetApplyProvider] 로그인 폼을 찾을 수 없습니다.");
            return false;
        }

        emailInput.first().fill(loginId);
        pwdInput.first().fill(password);
        page.locator("button[type='submit'], .btn_sign_in").first().click();
        
        playwrightManager.longDelay();

        if (page.url().contains("sign_in")) {
            log.warn("[JobPlanetApplyProvider] 잡플래닛 로그인 실패 (URL 변경 없음)");
            return false;
        }

        return true;
    }

    @Override
    public ApplyResult submit(Page page, PlaywrightManager playwrightManager, JobApplication app, List<Path> attachments) {
        log.info("[JobPlanetApplyProvider] 잡플래닛 지원 시작: {}", app.getJobPosting().getTitle());
        
        page.navigate(app.getJobPosting().getUrl());
        playwrightManager.shortDelay();

        Locator applyBtn = page.locator("button:has-text('지원하기'), a:has-text('지원하기')");
        if (applyBtn.count() == 0) {
            return ApplyResult.fail("지원하기 버튼을 찾을 수 없습니다.");
        }
        applyBtn.first().click();
        playwrightManager.shortDelay();

        Locator textArea = page.locator("textarea");
        if (textArea.count() > 0 && app.getCoverLetter() != null) {
            textArea.first().fill(app.getCoverLetter());
        }

        if (attachments != null && !attachments.isEmpty()) {
            Locator fileInput = page.locator("input[type='file']");
            if (fileInput.count() > 0) {
                Path[] pathArray = attachments.toArray(new Path[0]);
                fileInput.first().setInputFiles(pathArray);
            }
        }

        Locator submitBtn = page.locator("button[type='submit'], button:has-text('제출')");
        if (submitBtn.count() > 0) {
            submitBtn.first().click();
            playwrightManager.shortDelay();
        }

        return checkResult(page);
    }

    private ApplyResult checkResult(Page page) {
        Locator successMsg = page.locator("text=지원이 완료, text=입사지원 완료, text=성공");
        Locator errorMsg = page.locator("text=오류, text=실패, text=에러, .error");

        if (successMsg.count() > 0) {
            return ApplyResult.success();
        } else if (errorMsg.count() > 0) {
            String reason = errorMsg.first().textContent();
            return ApplyResult.fail(reason != null ? reason.trim() : "알 수 없는 오류");
        }
        return ApplyResult.unknown("제출 결과를 확인할 수 없습니다. 사후 검증이 필요합니다.");
    }
}
