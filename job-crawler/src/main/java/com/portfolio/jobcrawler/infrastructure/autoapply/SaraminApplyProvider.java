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
 * 사람인용 자동 지원 프로바이더 구현체
 */
@Slf4j
@Component
public class SaraminApplyProvider implements AutoApplyProvider {

    @Override
    public String getSiteName() {
        return "SARAMIN";
    }

    @Override
    public boolean login(Page page, PlaywrightManager playwrightManager, String loginId, String password) {
        log.info("[SaraminApplyProvider] 사람인 로그인 시도 - {}", loginId);
        page.navigate("https://www.saramin.co.kr/zf_user/auth");
        playwrightManager.shortDelay();

        Locator idInput = page.locator("#id");
        Locator pwdInput = page.locator("#password");
        
        if (idInput.count() == 0 || pwdInput.count() == 0) {
            log.warn("[SaraminApplyProvider] 로그인 폼을 찾을 수 없습니다.");
            return false;
        }

        idInput.fill(loginId);
        pwdInput.fill(password);
        page.locator("button[type='submit'], .btn_login").first().click();
        
        playwrightManager.longDelay();

        if (page.url().contains("auth") || page.locator(".error_message").count() > 0) {
            log.warn("[SaraminApplyProvider] 사람인 로그인 실패 (에러 메시지 발생 또는 리다이렉트 안됨)");
            return false;
        }

        return true;
    }

    @Override
    public ApplyResult submit(Page page, PlaywrightManager playwrightManager, JobApplication app, List<Path> attachments) {
        log.info("[SaraminApplyProvider] 사람인 지원 시작: {}", app.getJobPosting().getTitle());
        
        page.navigate(app.getJobPosting().getUrl());
        playwrightManager.shortDelay();

        Locator applyBtn = page.locator("button:has-text('입사지원'), a:has-text('입사지원')");
        if (applyBtn.count() == 0) {
            return ApplyResult.fail("입사지원 버튼을 찾을 수 없습니다.");
        }
        applyBtn.first().click();
        playwrightManager.shortDelay();

        Locator coverLetterField = page.locator("textarea[name*='cover'], textarea[name*='self']");
        if (coverLetterField.count() > 0 && app.getCoverLetter() != null) {
            coverLetterField.first().fill(app.getCoverLetter());
        }

        if (attachments != null && !attachments.isEmpty()) {
            Locator fileInput = page.locator("input[type='file']");
            if (fileInput.count() > 0) {
                Path[] pathArray = attachments.toArray(new Path[0]);
                fileInput.first().setInputFiles(pathArray);
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
