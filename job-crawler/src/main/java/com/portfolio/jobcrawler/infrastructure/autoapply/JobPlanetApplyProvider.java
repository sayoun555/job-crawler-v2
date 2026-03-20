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
public class JobPlanetApplyProvider implements AutoApplyProvider {

    @Override
    public String getSiteName() {
        return "JOBPLANET";
    }

    @Override
    public boolean login(Page page, PlaywrightManager playwrightManager, String loginId, String password) {
        log.info("[잡플래닛-지원] 로그인 시도 - {}", loginId);
        page.navigate("https://www.jobplanet.co.kr/users/sign_in");
        playwrightManager.shortDelay();

        Locator emailInput = page.locator("input[type='email'], input[name*='email']");
        Locator pwdInput = page.locator("input[type='password']");

        if (emailInput.count() == 0 || pwdInput.count() == 0) {
            log.warn("[잡플래닛-지원] 로그인 폼을 찾을 수 없습니다.");
            return false;
        }

        emailInput.first().fill(loginId);
        pwdInput.first().fill(password);
        page.locator("button[type='submit'], .btn_sign_in, button:has-text('로그인')").first().click();
        playwrightManager.longDelay();

        if (page.url().contains("sign_in")) {
            log.warn("[잡플래닛-지원] 로그인 실패");
            return false;
        }
        return true;
    }

    @Override
    public ApplyResult submit(Page page, PlaywrightManager playwrightManager, JobApplication app, List<Path> attachments) {
        log.info("[잡플래닛-지원] 지원 시작: {}", app.getJobPosting().getTitle());

        page.navigate(app.getJobPosting().getUrl());
        playwrightManager.longDelay();

        Locator applyBtn = page.locator(
                "button:has-text('지원하기'), a:has-text('지원하기'), button:has-text('입사지원')");

        if (applyBtn.count() == 0) {
            return ApplyResult.fail("지원 버튼을 찾을 수 없습니다.");
        }

        String href = applyBtn.first().getAttribute("href");
        if (href != null && !href.contains("jobplanet.co.kr")) {
            return ApplyResult.fail("외부 사이트 지원입니다. URL: " + href);
        }

        applyBtn.first().click();
        playwrightManager.longDelay();

        Page applyPage = getLatestPage(page);

        selectResume(applyPage);
        fillCoverLetter(applyPage, app.getCoverLetter());
        uploadAttachments(applyPage, attachments);
        checkAllAgreements(applyPage);

        applyPage.onDialog(dialog -> dialog.accept());

        Locator submitBtn = applyPage.locator(
                "button:has-text('지원완료'), button:has-text('제출'), button[type='submit']");
        if (submitBtn.count() == 0) {
            return ApplyResult.fail("제출 버튼을 찾을 수 없습니다.");
        }
        submitBtn.first().click();
        playwrightManager.longDelay();

        return checkResult(applyPage);
    }

    private Page getLatestPage(Page page) {
        if (page.context().pages().size() > 1) {
            Page latest = page.context().pages().get(page.context().pages().size() - 1);
            latest.waitForLoadState();
            return latest;
        }
        return page;
    }

    private void selectResume(Page page) {
        Locator resume = page.locator("input[type='radio'][name*='resume']");
        if (resume.count() > 0) resume.first().check();
    }

    private void fillCoverLetter(Page page, String coverLetter) {
        if (coverLetter == null || coverLetter.isBlank()) return;
        Locator textareas = page.locator("textarea");
        for (int i = 0; i < textareas.count(); i++) {
            if (textareas.nth(i).inputValue().isBlank()) {
                textareas.nth(i).fill(coverLetter);
                log.info("[잡플래닛-지원] 자소서 입력 완료");
                return;
            }
        }
    }

    private void uploadAttachments(Page page, List<Path> attachments) {
        if (attachments == null || attachments.isEmpty()) return;
        Locator fileInput = page.locator("input[type='file']");
        if (fileInput.count() > 0) {
            fileInput.first().setInputFiles(attachments.toArray(new Path[0]));
        }
    }

    private void checkAllAgreements(Page page) {
        Locator checkboxes = page.locator("input[type='checkbox']");
        for (int i = 0; i < checkboxes.count(); i++) {
            if (!checkboxes.nth(i).isChecked()) checkboxes.nth(i).check();
        }
    }

    private ApplyResult checkResult(Page page) {
        page.waitForTimeout(2000);
        if (page.locator("text=지원 완료, text=지원이 완료, text=성공").count() > 0) {
            return ApplyResult.success();
        }
        if (page.locator(".error, text=실패, text=오류").count() > 0) {
            return ApplyResult.fail(page.locator(".error, text=실패").first().textContent());
        }
        return ApplyResult.unknown("제출 결과를 확인할 수 없습니다.");
    }
}
