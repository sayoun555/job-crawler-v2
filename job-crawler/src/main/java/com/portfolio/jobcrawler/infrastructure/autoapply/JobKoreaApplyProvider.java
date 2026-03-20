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
public class JobKoreaApplyProvider implements AutoApplyProvider {

    @Override
    public String getSiteName() {
        return "JOBKOREA";
    }

    @Override
    public boolean login(Page page, PlaywrightManager playwrightManager, String loginId, String password) {
        log.info("[잡코리아-지원] 로그인 시도 - {}", loginId);
        page.navigate("https://www.jobkorea.co.kr/Login/Login_Tot.asp");
        playwrightManager.shortDelay();

        Locator idInput = page.locator("#userId, input[name='userId']");
        Locator pwdInput = page.locator("#password, input[name='password']");

        if (idInput.count() == 0 || pwdInput.count() == 0) {
            log.warn("[잡코리아-지원] 로그인 폼을 찾을 수 없습니다.");
            return false;
        }

        idInput.first().click();
        idInput.first().type(loginId, new Locator.TypeOptions().setDelay(30));
        pwdInput.first().click();
        pwdInput.first().type(password, new Locator.TypeOptions().setDelay(30));
        page.locator("button[type='submit'], .btn-login, button:has-text('로그인')").first().click();

        playwrightManager.longDelay();

        if (page.url().toLowerCase().contains("/login") || page.locator(".error-message, .err_msg").count() > 0) {
            log.warn("[잡코리아-지원] 로그인 실패");
            return false;
        }
        return true;
    }

    @Override
    public ApplyResult submit(Page page, PlaywrightManager playwrightManager, JobApplication app, List<Path> attachments) {
        String title = app.getJobPosting().getTitle();
        log.info("[잡코리아-지원] 지원 시작: {}", title);

        // 1. 공고 페이지 이동
        page.navigate(app.getJobPosting().getUrl());
        playwrightManager.longDelay();

        // 2. 즉시지원 버튼 (잡코리아는 "즉시지원" 또는 "입사지원")
        Locator applyBtn = page.locator(
                "button:has-text('즉시지원'), " +
                "a:has-text('즉시지원'), " +
                "button:has-text('입사지원'), " +
                ".btn-apply");

        if (applyBtn.count() == 0) {
            Locator homepageBtn = page.locator("a:has-text('홈페이지 지원'), a:has-text('기업홈페이지')");
            if (homepageBtn.count() > 0) {
                return ApplyResult.fail("홈페이지 지원만 가능합니다.");
            }
            return ApplyResult.fail("지원 버튼을 찾을 수 없습니다.");
        }

        // 3. confirm 다이얼로그 자동 승인 (클릭 전 등록해야 누락 방지)
        page.onDialog(dialog -> {
            log.info("[잡코리아-지원] 다이얼로그: {}", dialog.message());
            dialog.accept();
        });

        applyBtn.first().click();
        playwrightManager.longDelay();

        // 4. 새 탭/팝업 처리
        Page applyPage = page;
        if (page.context().pages().size() > 1) {
            applyPage = page.context().pages().get(page.context().pages().size() - 1);
            applyPage.waitForLoadState();
        }

        // 4. 이력서 선택 (잡코리아는 등록된 이력서 목록에서 선택)
        Locator resumeSelect = applyPage.locator("input[type='radio'][name*='resume'], .resume-item input[type='radio']");
        if (resumeSelect.count() > 0) {
            resumeSelect.first().check();
            log.info("[잡코리아-지원] 이력서 선택 완료");
        }

        // 5. 자소서 입력
        fillCoverLetter(applyPage, app.getCoverLetter());

        // 6. 첨부파일
        if (attachments != null && !attachments.isEmpty()) {
            Locator fileInput = applyPage.locator("input[type='file']");
            if (fileInput.count() > 0) {
                fileInput.first().setInputFiles(attachments.toArray(new Path[0]));
                log.info("[잡코리아-지원] 첨부파일 {} 개 업로드", attachments.size());
            }
        }

        // 7. 약관 동의
        Locator agreeAll = applyPage.locator("input[type='checkbox'][id*='agree'], label:has-text('동의') input[type='checkbox']");
        for (int i = 0; i < agreeAll.count(); i++) {
            if (!agreeAll.nth(i).isChecked()) {
                agreeAll.nth(i).check();
            }
        }

        // 8. 제출
        Locator submitBtn = applyPage.locator(
                "button:has-text('지원완료'), " +
                "button:has-text('입사지원'), " +
                "button:has-text('제출'), " +
                "button[type='submit']");

        if (submitBtn.count() == 0) {
            return ApplyResult.fail("제출 버튼을 찾을 수 없습니다.");
        }

        submitBtn.first().click();
        playwrightManager.longDelay();

        return checkResult(applyPage);
    }

    private void fillCoverLetter(Page page, String coverLetter) {
        if (coverLetter == null || coverLetter.isBlank()) return;

        Locator textareas = page.locator("textarea");
        for (int i = 0; i < textareas.count(); i++) {
            Locator ta = textareas.nth(i);
            String name = ta.getAttribute("name");
            if (name != null && (name.contains("cover") || name.contains("self") || name.contains("intro"))) {
                ta.fill(coverLetter);
                log.info("[잡코리아-지원] 자소서 입력 완료 ({})", name);
                return;
            }
        }
        // 첫 번째 빈 textarea에 입력
        for (int i = 0; i < textareas.count(); i++) {
            if (textareas.nth(i).inputValue().isBlank()) {
                textareas.nth(i).fill(coverLetter);
                log.info("[잡코리아-지원] 자소서 입력 (첫 번째 빈 textarea)");
                return;
            }
        }
    }

    private ApplyResult checkResult(Page page) {
        page.waitForTimeout(2000);

        String[] successPatterns = {"지원이 완료", "지원완료", "입사지원 완료", "정상적으로 접수"};
        for (String pattern : successPatterns) {
            if (page.locator("text=" + pattern).count() > 0) {
                return ApplyResult.success();
            }
        }

        if (page.url().contains("success") || page.url().contains("complete")) {
            return ApplyResult.success();
        }

        Locator errorMsg = page.locator(".error, .err_msg, text=오류, text=실패");
        if (errorMsg.count() > 0) {
            return ApplyResult.fail(errorMsg.first().textContent());
        }

        return ApplyResult.unknown("제출 결과를 확인할 수 없습니다.");
    }
}
