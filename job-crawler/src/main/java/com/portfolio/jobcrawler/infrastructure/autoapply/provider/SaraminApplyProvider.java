package com.portfolio.jobcrawler.infrastructure.autoapply.provider;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.portfolio.jobcrawler.domain.jobapply.entity.JobApplication;
import com.portfolio.jobcrawler.infrastructure.autoapply.ApplyResult;
import com.portfolio.jobcrawler.infrastructure.autoapply.AutoApplyProvider;
import com.portfolio.jobcrawler.infrastructure.autoapply.CoverLetterFiller;
import com.portfolio.jobcrawler.infrastructure.crawler.PlaywrightManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Slf4j
@Component
public class SaraminApplyProvider implements AutoApplyProvider {

    @Override
    public String getSiteName() {
        return "SARAMIN";
    }

    @Override
    public boolean login(Page page, PlaywrightManager playwrightManager, String loginId, String password) {
        log.info("[사람인-지원] 로그인 시도 - {}", loginId);
        page.navigate("https://www.saramin.co.kr/zf_user/auth");
        playwrightManager.shortDelay();

        Locator idInput = page.locator("#id");
        Locator pwdInput = page.locator("#password");

        if (idInput.count() == 0 || pwdInput.count() == 0) {
            log.warn("[사람인-지원] 로그인 폼을 찾을 수 없습니다.");
            return false;
        }

        // jQuery 이벤트 호환: fill() 대신 click+type
        idInput.click();
        idInput.type(loginId, new Locator.TypeOptions().setDelay(50));
        pwdInput.click();
        pwdInput.type(password, new Locator.TypeOptions().setDelay(50));

        playwrightManager.shortDelay();
        page.locator("button.btn_login").click();
        playwrightManager.longDelay();

        if (page.url().contains("/auth") || page.locator(".error_message:visible").count() > 0) {
            log.warn("[사람인-지원] 로그인 실패");
            return false;
        }

        return true;
    }

    @Override
    public ApplyResult submit(Page page, PlaywrightManager playwrightManager, JobApplication app, List<Path> attachments) {
        String title = app.getJobPosting().getTitle();
        log.info("[사람인-지원] 지원 시작: {}", title);

        // 1. 공고 페이지로 이동
        page.navigate(app.getJobPosting().getUrl());
        playwrightManager.longDelay();

        // 2. 입사지원 버튼 찾기 (사람인은 다양한 형태)
        Locator applyBtn = page.locator(
                "button.btn_apply, " +
                "a.btn_apply, " +
                "button:has-text('입사지원'), " +
                "a:has-text('입사지원'), " +
                "button:has-text('온라인 입사지원')");

        if (applyBtn.count() == 0) {
            // 홈페이지 지원인 경우
            Locator homepageBtn = page.locator("a:has-text('홈페이지 지원'), a:has-text('기업 홈페이지')");
            if (homepageBtn.count() > 0) {
                return ApplyResult.fail("이 공고는 홈페이지 지원만 가능합니다. URL: " + homepageBtn.first().getAttribute("href"));
            }
            return ApplyResult.fail("입사지원 버튼을 찾을 수 없습니다.");
        }

        // 3. confirm 다이얼로그 자동 승인 (클릭 전 등록해야 누락 방지)
        page.onDialog(dialog -> {
            log.info("[사람인-지원] 다이얼로그: {}", dialog.message());
            dialog.accept();
        });

        applyBtn.first().click();
        playwrightManager.longDelay();

        // 4. 팝업/새 탭 처리 (사람인은 지원 팝업을 띄울 수 있음)
        Page applyPage = page;
        if (page.context().pages().size() > 1) {
            applyPage = page.context().pages().get(page.context().pages().size() - 1);
            applyPage.waitForLoadState();
        }

        // 4. 이력서 선택 (사람인은 기본 이력서가 선택되어 있는 경우가 많음)
        Locator resumeRadio = applyPage.locator("input[type='radio'][name*='resume'], input[type='radio'][name*='이력서']");
        if (resumeRadio.count() > 0) {
            resumeRadio.first().check();
            log.info("[사람인-지원] 이력서 선택 완료");
        }

        // 5. 자소서 입력 (커스텀 섹션이면 다중 textarea 매핑)
        CoverLetterFiller.fill(applyPage, app);

        // 6. 첨부파일 업로드
        uploadAttachments(applyPage, attachments);

        // 7. 약관 동의 체크박스 (있으면)
        Locator agreeCheck = applyPage.locator("input[type='checkbox'][name*='agree'], input[type='checkbox']#agree");
        if (agreeCheck.count() > 0) {
            for (int i = 0; i < agreeCheck.count(); i++) {
                if (!agreeCheck.nth(i).isChecked()) {
                    agreeCheck.nth(i).check();
                }
            }
        }

        // 8. 제출
        Locator submitBtn = applyPage.locator(
                "button:has-text('지원완료'), " +
                "button:has-text('입사지원'), " +
                "button[type='submit']:has-text('제출'), " +
                "button:has-text('지원하기')");

        if (submitBtn.count() == 0) {
            return ApplyResult.fail("제출 버튼을 찾을 수 없습니다.");
        }

        submitBtn.first().click();
        playwrightManager.longDelay();

        return checkResult(applyPage);
    }

    private void uploadAttachments(Page page, List<Path> attachments) {
        if (attachments == null || attachments.isEmpty()) return;

        Locator fileInput = page.locator("input[type='file']");
        if (fileInput.count() > 0) {
            fileInput.first().setInputFiles(attachments.toArray(new Path[0]));
            log.info("[사람인-지원] 첨부파일 {} 개 업로드", attachments.size());
        }
    }

    private ApplyResult checkResult(Page page) {
        page.waitForTimeout(2000);

        // 성공 패턴
        String[] successPatterns = {"지원이 완료", "입사지원 완료", "지원완료", "정상적으로 접수"};
        for (String pattern : successPatterns) {
            if (page.locator("text=" + pattern).count() > 0) {
                return ApplyResult.success();
            }
        }

        // URL 변화로 성공 판단
        if (page.url().contains("apply_success") || page.url().contains("complete")) {
            return ApplyResult.success();
        }

        // 실패 패턴
        Locator errorMsg = page.locator(".error, .err_msg, text=오류, text=실패");
        if (errorMsg.count() > 0) {
            String reason = errorMsg.first().textContent();
            return ApplyResult.fail(reason != null ? reason.trim() : "알 수 없는 오류");
        }

        return ApplyResult.unknown("제출 결과를 확인할 수 없습니다. 사이트에서 직접 확인해주세요.");
    }
}
