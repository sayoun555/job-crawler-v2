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
public class LinkareerApplyProvider implements AutoApplyProvider {

    @Override
    public String getSiteName() {
        return "LINKAREER";
    }

    @Override
    public boolean login(Page page, PlaywrightManager playwrightManager, String loginId, String password) {
        log.info("[링커리어-지원] 로그인 시도 - {}", loginId);
        page.navigate("https://linkareer.com/login");
        playwrightManager.shortDelay();

        Locator emailInput = page.locator("input[type='email'], input[name='email'], input[placeholder*='이메일']");
        Locator pwdInput = page.locator("input[type='password'], input[name='password']");

        if (emailInput.count() == 0 || pwdInput.count() == 0) {
            log.warn("[링커리어-지원] 로그인 폼을 찾을 수 없습니다.");
            return false;
        }

        emailInput.first().fill(loginId);
        pwdInput.first().fill(password);
        page.locator("button[type='submit'], button:has-text('로그인')").first().click();

        playwrightManager.longDelay();

        if (page.url().contains("/login")) {
            log.warn("[링커리어-지원] 로그인 실패 (URL 변경 없음)");
            return false;
        }

        return true;
    }

    @Override
    public ApplyResult submit(Page page, PlaywrightManager playwrightManager, JobApplication app, List<Path> attachments) {
        String title = app.getJobPosting().getTitle();
        log.info("[링커리어-지원] 지원 시작: {}", title);

        // 1. 공고 페이지로 이동
        page.navigate(app.getJobPosting().getUrl());
        playwrightManager.longDelay();

        // 2. 지원 버튼 탐색 (링커리어는 '간편지원', '지원하기', '바로지원' 등 다양한 형태)
        Locator applyBtn = page.locator(
                "button:has-text('간편지원'), " +
                "button:has-text('지원하기'), " +
                "a:has-text('지원하기'), " +
                "button:has-text('바로지원'), " +
                "button:has-text('입사지원')");

        if (applyBtn.count() == 0) {
            // 외부 사이트 지원 링크 확인
            Locator externalLink = page.locator(
                    "a:has-text('지원하기'), a:has-text('홈페이지'), a:has-text('채용 페이지')");
            if (externalLink.count() > 0) {
                String href = externalLink.first().getAttribute("href");
                if (href != null && !href.contains("linkareer.com")) {
                    return ApplyResult.fail("외부 사이트 지원입니다. URL: " + href);
                }
            }
            return ApplyResult.fail("지원 버튼을 찾을 수 없습니다.");
        }

        // 3. confirm 다이얼로그 자동 승인 (미리 등록)
        page.onDialog(dialog -> {
            log.info("[링커리어-지원] 다이얼로그: {}", dialog.message());
            dialog.accept();
        });

        applyBtn.first().click();
        playwrightManager.longDelay();

        // 4. 팝업/새 탭 처리 (링커리어는 SPA지만 일부 공고는 새 탭으로 열릴 수 있음)
        Page applyPage = getLatestPage(page);

        // 5. 이력서 선택 (라디오 버튼 또는 셀렉트)
        selectResume(applyPage);

        // 6. 자소서 입력 (커스텀 섹션이면 다중 textarea 매핑)
        CoverLetterFiller.fill(applyPage, app);

        // 7. 첨부파일 업로드
        uploadAttachments(applyPage, attachments);

        // 8. 약관 동의 체크박스
        checkAllAgreements(applyPage);

        // 9. 제출 버튼
        Locator submitBtn = applyPage.locator(
                "button:has-text('지원 완료'), " +
                "button:has-text('지원완료'), " +
                "button:has-text('제출'), " +
                "button:has-text('제출하기'), " +
                "button[type='submit']:has-text('지원')");

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
        // 링커리어 이력서 선택: 라디오 버튼 또는 셀렉트박스
        Locator resumeRadio = page.locator(
                "input[type='radio'][name*='resume'], " +
                "input[type='radio'][name*='이력서'], " +
                ".resume-item input[type='radio']");
        if (resumeRadio.count() > 0) {
            resumeRadio.first().check();
            log.info("[링커리어-지원] 이력서 선택 완료 (라디오)");
            return;
        }

        // 셀렉트박스 형태
        Locator resumeSelect = page.locator("select[name*='resume'], select[name*='이력서']");
        if (resumeSelect.count() > 0) {
            // 첫 번째 옵션이 아닌 두 번째 옵션 선택 (보통 첫 번째는 "선택하세요")
            Locator options = resumeSelect.first().locator("option");
            if (options.count() > 1) {
                String value = options.nth(1).getAttribute("value");
                if (value != null) {
                    resumeSelect.first().selectOption(value);
                    log.info("[링커리어-지원] 이력서 선택 완료 (셀렉트)");
                }
            }
        }
    }

    private void uploadAttachments(Page page, List<Path> attachments) {
        if (attachments == null || attachments.isEmpty()) return;

        Locator fileInput = page.locator("input[type='file']");
        if (fileInput.count() > 0) {
            fileInput.first().setInputFiles(attachments.toArray(new Path[0]));
            log.info("[링커리어-지원] 첨부파일 {} 개 업로드", attachments.size());
        }
    }

    private void checkAllAgreements(Page page) {
        // 전체 동의 버튼 우선 탐색
        Locator agreeAll = page.locator(
                "input[type='checkbox'][id*='all'], " +
                "label:has-text('전체 동의') input[type='checkbox'], " +
                "label:has-text('모두 동의') input[type='checkbox']");
        if (agreeAll.count() > 0 && !agreeAll.first().isChecked()) {
            agreeAll.first().check();
            log.info("[링커리어-지원] 전체 동의 체크");
            return;
        }

        // 개별 동의 체크박스
        Locator agreements = page.locator(
                "input[type='checkbox'][name*='agree'], " +
                "input[type='checkbox'][id*='agree'], " +
                "label:has-text('동의') input[type='checkbox'], " +
                "label:has-text('개인정보') input[type='checkbox']");
        for (int i = 0; i < agreements.count(); i++) {
            if (!agreements.nth(i).isChecked()) {
                agreements.nth(i).check();
            }
        }
        if (agreements.count() > 0) {
            log.info("[링커리어-지원] 약관 동의 {} 건 체크", agreements.count());
        }
    }

    private ApplyResult checkResult(Page page) {
        page.waitForTimeout(2000);

        // 성공 패턴
        String[] successPatterns = {"지원이 완료", "지원 완료", "지원완료", "성공적으로 제출", "정상적으로 접수"};
        for (String pattern : successPatterns) {
            if (page.locator("text=" + pattern).count() > 0) {
                return ApplyResult.success();
            }
        }

        // URL 변화로 성공 판단
        if (page.url().contains("success") || page.url().contains("complete") || page.url().contains("done")) {
            return ApplyResult.success();
        }

        // 실패 패턴
        Locator errorMsg = page.locator(".error, .err_msg, text=오류, text=실패, text=에러");
        if (errorMsg.count() > 0) {
            String reason = errorMsg.first().textContent();
            return ApplyResult.fail(reason != null ? reason.trim() : "알 수 없는 오류");
        }

        return ApplyResult.unknown("제출 결과를 확인할 수 없습니다. 사이트에서 직접 확인해주세요.");
    }
}
