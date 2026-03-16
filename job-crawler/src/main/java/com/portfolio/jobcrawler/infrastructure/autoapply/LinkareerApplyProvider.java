package com.portfolio.jobcrawler.infrastructure.autoapply;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.portfolio.jobcrawler.domain.jobapply.entity.JobApplication;
import com.portfolio.jobcrawler.infrastructure.crawler.PlaywrightManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Slf4j
@Component
public class LinkareerApplyProvider implements AutoApplyProvider {

    @Override
    public String getSiteName() {
        return "LINKAREER";
    }

    @Override
    public boolean login(Page page, PlaywrightManager playwrightManager, String loginId, String password) {
        log.info("[LinkareerApplyProvider] 링커리어 로그인 시도 - {}", loginId);
        page.navigate("https://linkareer.com/login");
        playwrightManager.shortDelay();

        Locator emailInput = page.locator("input[type='email'], input[name='email'], input[placeholder*='이메일']");
        Locator pwdInput = page.locator("input[type='password'], input[name='password']");

        if (emailInput.count() == 0 || pwdInput.count() == 0) {
            log.warn("[LinkareerApplyProvider] 로그인 폼을 찾을 수 없습니다.");
            return false;
        }

        emailInput.first().fill(loginId);
        pwdInput.first().fill(password);
        page.locator("button[type='submit'], button:has-text('로그인')").first().click();

        playwrightManager.longDelay();

        if (page.url().contains("/login")) {
            log.warn("[LinkareerApplyProvider] 링커리어 로그인 실패 (URL 변경 없음)");
            return false;
        }

        return true;
    }

    @Override
    public ApplyResult submit(Page page, PlaywrightManager playwrightManager, JobApplication app, List<Path> attachments) {
        log.info("[LinkareerApplyProvider] 링커리어 지원 시작: {}", app.getJobPosting().getTitle());

        page.navigate(app.getJobPosting().getUrl());
        playwrightManager.shortDelay();

        Locator applyBtn = page.locator("button:has-text('지원하기'), a:has-text('지원하기'), button:has-text('입사지원')");
        if (applyBtn.count() == 0) {
            return ApplyResult.fail("지원 버튼을 찾을 수 없습니다.");
        }
        applyBtn.first().click();
        playwrightManager.shortDelay();

        Locator coverLetterField = page.locator("textarea");
        if (coverLetterField.count() > 0 && app.getCoverLetter() != null) {
            coverLetterField.first().fill(app.getCoverLetter());
        }

        if (attachments != null && !attachments.isEmpty()) {
            Locator fileInput = page.locator("input[type='file']");
            if (fileInput.count() > 0) {
                fileInput.first().setInputFiles(attachments.toArray(new Path[0]));
            }
        }

        Locator submitBtn = page.locator("button[type='submit'], button:has-text('제출'), button:has-text('지원 완료')");
        if (submitBtn.count() > 0) {
            submitBtn.first().click();
            playwrightManager.shortDelay();
        }

        return checkResult(page);
    }

    private ApplyResult checkResult(Page page) {
        Locator successMsg = page.locator("text=지원이 완료, text=지원 완료, text=성공");
        Locator errorMsg = page.locator("text=오류, text=실패, text=에러, .error");

        if (successMsg.count() > 0) {
            return ApplyResult.success();
        } else if (errorMsg.count() > 0) {
            String reason = errorMsg.first().textContent();
            return ApplyResult.fail(reason != null ? reason.trim() : "알 수 없는 오류");
        }
        return ApplyResult.unknown("제출 결과를 확인할 수 없습니다.");
    }
}
