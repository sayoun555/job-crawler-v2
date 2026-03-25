package com.portfolio.jobcrawler.infrastructure.resumesync.provider;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.portfolio.jobcrawler.application.ai.dto.CoverLetterSection;
import com.portfolio.jobcrawler.domain.resume.entity.*;
import com.portfolio.jobcrawler.domain.resume.vo.SchoolType;
import com.portfolio.jobcrawler.infrastructure.crawler.PlaywrightManager;
import com.portfolio.jobcrawler.infrastructure.resumesync.ResumeProvider;
import com.portfolio.jobcrawler.infrastructure.resumesync.ResumeSyncResult;
import com.portfolio.jobcrawler.infrastructure.resumesync.ResumeSyncResult.Builder;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 링커리어 이력서 동기화 구현체.
 * 링커리어 이력서 작성 페이지(https://linkareer.com/my-career/resume/write)의
 * 실제 HTML 구조에 맞춘 텍스트 기반 로케이터를 사용한다.
 *
 * 핵심 규칙:
 * - Playwright의 getByRole/getByLabel/getByPlaceholder 텍스트 기반 로케이터 사용
 * - 기존 데이터가 있으면 clear 후 입력
 * - 각 섹션별 SRP 준수 (메서드 분리)
 * - 사이드바 "섹션 추가 아이콘" 버튼으로 미표시 섹션 활성화
 */
@Slf4j
@Component
public class LinkareerResumeProvider implements ResumeProvider {

    private static final String SITE_NAME = "LINKAREER";
    private static final String RESUME_LIST_URL =
            "https://linkareer.com/my-career/resume";
    private static final String RESUME_WRITE_URL =
            "https://linkareer.com/my-career/resume/write";

    /** 링커리어 학교구분 텍스트 매핑 */
    private static final Map<SchoolType, String> SCHOOL_TYPE_LABEL_MAP = Map.of(
            SchoolType.HIGH_SCHOOL, "고등학교",
            SchoolType.COLLEGE_2Y, "대학교(2,3년)",
            SchoolType.COLLEGE_4Y, "대학교(4년)",
            SchoolType.GRADUATE_MASTER, "대학원(석사)",
            SchoolType.GRADUATE_DOCTOR, "대학원(박사)"
    );

    @Override
    public String getSiteName() {
        return SITE_NAME;
    }

    @Override
    public ResumeSyncResult syncResume(Page page, PlaywrightManager pm, Resume resume) {
        log.info("[LinkareerResume] 이력서 동기화 시작");

        try {
            // beforeunload 다이얼로그 자동 수락
            page.onDialog(dialog -> {
                log.info("[LinkareerResume] 다이얼로그 자동 수락: {}", dialog.message());
                dialog.accept();
            });

            navigateToResumePage(page, pm);

            String currentUrl = page.url();
            if (isLoginRedirect(currentUrl)) {
                log.error("[LinkareerResume] 로그인 세션 만료: {}", currentUrl);
                return ResumeSyncResult.sessionExpired(
                        "링커리어 로그인 세션이 만료되었습니다. 설정에서 다시 연동해주세요.");
            }

            log.info("[LinkareerResume] 이력서 작성 페이지 로드 완료: {}", currentUrl);

            // confirm/alert 자동 수락 오버라이드
            overrideDialogs(page);

            ResumeSyncResult.Builder result = ResumeSyncResult.builder();

            fillBasicInfo(page, pm, resume, result);
            fillSkills(page, pm, resume, result);
            fillEducations(page, pm, resume, result);
            fillCareers(page, pm, resume, result);
            fillActivities(page, pm, resume, result);
            fillSelfIntroduction(page, pm, resume, result);
            saveResume(page, pm, result);

            return result.build();

        } catch (Exception e) {
            log.error("[LinkareerResume] 동기화 실패: {}", e.getMessage(), e);
            return ResumeSyncResult.fail(e.getMessage());
        }
    }

    @Override
    public ResumeSyncResult updateSelfIntroduction(Page page, PlaywrightManager pm,
                                                   List<CoverLetterSection> sections) {
        log.info("[LinkareerResume] 자기소개서 업데이트 시작 ({}개 문항)", sections.size());

        try {
            // beforeunload 다이얼로그 자동 수락
            page.onDialog(dialog -> {
                log.info("[LinkareerResume] 다이얼로그 자동 수락: {}", dialog.message());
                dialog.accept();
            });

            navigateToResumePage(page, pm);

            String currentUrl = page.url();
            if (isLoginRedirect(currentUrl)) {
                log.error("[LinkareerResume] 로그인 세션 만료: {}", currentUrl);
                return ResumeSyncResult.sessionExpired(
                        "링커리어 로그인 세션이 만료되었습니다. 설정에서 다시 연동해주세요.");
            }

            log.info("[LinkareerResume] 이력서 페이지 로드 완료: {}", currentUrl);

            // confirm/alert 자동 수락 오버라이드
            overrideDialogs(page);

            // 사이드바에서 자기소개서 섹션 추가
            ensureSectionVisible(page, "자기소개서", pm);
            scrollToHeading(page, "자기소개서", pm);

            // 기존 자기소개서 항목 모두 삭제
            deleteAllCoverLetterItems(page, pm);

            // 각 문항 입력
            for (int i = 0; i < sections.size(); i++) {
                CoverLetterSection section = sections.get(i);

                // 두 번째 문항부터 "자기소개서 항목 추가" 버튼 클릭
                if (i > 0) {
                    clickButtonContainingText(page, "자기소개서 항목 추가", pm);
                }

                // 제목 입력 (name="coverLetter.{i}.title")
                String titleSelector = "input[name='coverLetter." + i + ".title']";
                Locator titleInput = page.locator(titleSelector);
                if (titleInput.count() > 0) {
                    titleInput.first().click();
                    titleInput.first().fill("");
                    pm.randomDelay(100, 200);
                    titleInput.first().fill(section.title());
                    pm.randomDelay(300, 500);
                } else {
                    // 폴백: getByRole로 제목 textbox 찾기
                    Locator fallbackTitle = page.getByRole(AriaRole.TEXTBOX,
                            new Page.GetByRoleOptions().setName("제목"));
                    if (fallbackTitle.count() > i) {
                        fallbackTitle.nth(i).click();
                        fallbackTitle.nth(i).fill("");
                        pm.randomDelay(100, 200);
                        fallbackTitle.nth(i).fill(section.title());
                        pm.randomDelay(300, 500);
                    }
                }

                // 내용 입력 (name="coverLetter.{i}.content")
                String contentSelector = "textarea[name='coverLetter." + i + ".content']";
                Locator contentTextarea = page.locator(contentSelector);
                if (contentTextarea.count() > 0) {
                    contentTextarea.first().click();
                    contentTextarea.first().fill("");
                    pm.randomDelay(100, 200);
                    contentTextarea.first().fill(section.content());
                    pm.randomDelay(300, 500);
                } else {
                    // 폴백: 자기소개서 영역의 i번째 textarea
                    Locator fallbackTextarea = page.locator("textarea");
                    if (fallbackTextarea.count() > i) {
                        fallbackTextarea.nth(i).click();
                        fallbackTextarea.nth(i).fill("");
                        pm.randomDelay(100, 200);
                        fallbackTextarea.nth(i).fill(section.content());
                        pm.randomDelay(300, 500);
                    }
                }

                log.info("[LinkareerResume] 자기소개서 문항 입력 완료: {}", section.title());
            }

            // 저장하기 버튼 클릭
            Locator saveBtn = page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("저장하기"));
            if (saveBtn.count() == 0) {
                saveBtn = page.locator("button:has-text('저장하기'), button:has-text('저장')");
            }
            if (saveBtn.count() > 0) {
                saveBtn.first().scrollIntoViewIfNeeded();
                pm.randomDelay(500, 1000);
                saveBtn.first().click();
                pm.longDelay();
            }

            log.info("[LinkareerResume] 자기소개서 업데이트 완료 ({}개 문항)", sections.size());
            return ResumeSyncResult.success();

        } catch (Exception e) {
            log.error("[LinkareerResume] 자기소개서 업데이트 실패: {}", e.getMessage(), e);
            return ResumeSyncResult.fail(e.getMessage());
        }
    }

    /**
     * 기존 자기소개서 항목을 모두 삭제한다 (close-btn 반복 클릭).
     */
    private void deleteAllCoverLetterItems(Page page, PlaywrightManager pm) {
        for (int i = 0; i < 20; i++) {
            Locator closeBtn = page.locator("button.close-btn");
            if (closeBtn.count() == 0) {
                // 폴백: 자기소개서 섹션 내의 삭제 버튼
                closeBtn = page.getByRole(AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("삭제"));
            }
            if (closeBtn.count() == 0) break;
            closeBtn.first().click();
            pm.randomDelay(500, 800);
        }
        pm.shortDelay();
    }

    // ══════════════════════════════════════════════
    //  페이지 네비게이션
    // ══════════════════════════════════════════════

    /**
     * 이력서 목록 페이지를 먼저 확인하고, 기존 이력서가 있으면 편집, 없으면 새 작성 페이지로 이동한다.
     */
    private void navigateToResumePage(Page page, PlaywrightManager pm) {
        page.navigate(RESUME_LIST_URL,
                new Page.NavigateOptions().setWaitUntil(
                        com.microsoft.playwright.options.WaitUntilState.LOAD));
        pm.shortDelay();

        String currentUrl = page.url();
        if (isLoginRedirect(currentUrl)) {
            return; // 세션 만료는 호출부에서 처리
        }

        // 기존 이력서가 있으면 첫 번째 이력서 클릭하여 편집
        Locator existingResume = page.locator("a[href*='/my-career/resume/']")
                .filter(new Locator.FilterOptions().setHasNotText("write"));
        if (existingResume.count() > 0) {
            log.info("[LinkareerResume] 기존 이력서 발견 - 편집 모드");
            existingResume.first().click();
            pm.shortDelay();
            return;
        }

        // 기존 이력서 없으면 새 이력서 작성 페이지로 이동
        Locator newResumeLink = page.getByRole(AriaRole.LINK,
                new Page.GetByRoleOptions().setName("새 이력서 작성"));
        if (newResumeLink.count() > 0) {
            log.info("[LinkareerResume] 새 이력서 작성 링크 클릭");
            newResumeLink.first().click();
            pm.shortDelay();
            return;
        }

        // 폴백: 직접 write URL로 이동
        log.info("[LinkareerResume] 직접 이력서 작성 URL로 이동");
        page.navigate(RESUME_WRITE_URL,
                new Page.NavigateOptions().setWaitUntil(
                        com.microsoft.playwright.options.WaitUntilState.LOAD));
        pm.shortDelay();
    }

    // ══════════════════════════════════════════════
    //  기본 정보 (이름, 생년월일, 전화, 이메일)
    // ══════════════════════════════════════════════

    private void fillBasicInfo(Page page, PlaywrightManager pm, Resume resume,
                               ResumeSyncResult.Builder result) {
        try {
            scrollToHeading(page, "기본 정보", pm);

            fillTextboxByLabel(page, "이름", resume.getName(), pm);
            fillTextboxByLabel(page, "생년월일", toLinkareerBirthDate(resume.getBirthDate()), pm);
            fillPhoneTextbox(page, resume.getPhone(), pm);
            fillTextboxByLabel(page, "이메일", resume.getEmail(), pm);

            result.addSectionSuccess("기본정보");
            log.info("[LinkareerResume] 기본정보 입력 완료");

        } catch (Exception e) {
            log.error("[LinkareerResume] 기본정보 입력 실패: {}", e.getMessage(), e);
            result.addSectionFail("기본정보", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════
    //  보유 기술
    // ══════════════════════════════════════════════

    private void fillSkills(Page page, PlaywrightManager pm, Resume resume,
                            ResumeSyncResult.Builder result) {
        try {
            List<ResumeSkill> skills = resume.getSkills();
            if (skills == null || skills.isEmpty()) {
                result.addSectionResult("보유기술", true, "스킵 (데이터 없음)");
                return;
            }

            scrollToHeading(page, "보유 기술", pm);

            int addedCount = 0;
            for (ResumeSkill skill : skills) {
                String skillName = skill.getSkillName();
                if (skillName == null || skillName.isBlank()) continue;

                if (addSingleSkill(page, pm, skillName)) {
                    addedCount++;
                }
            }

            result.addSectionResult("보유기술", true,
                    String.format("%d/%d건 입력 완료", addedCount, skills.size()));
            log.info("[LinkareerResume] 보유기술 입력 완료 ({}/{}건)", addedCount, skills.size());

        } catch (Exception e) {
            log.error("[LinkareerResume] 보유기술 입력 실패: {}", e.getMessage(), e);
            result.addSectionFail("보유기술", e.getMessage());
        }
    }

    /**
     * 보유기술 입력창에 스킬명을 타이핑하고 "등록" 버튼을 클릭한다.
     */
    private boolean addSingleSkill(Page page, PlaywrightManager pm, String skillName) {
        try {
            Locator skillInput = page.getByPlaceholder("보유기술을 입력해주세요.");
            if (skillInput.count() == 0) {
                log.warn("[LinkareerResume] 보유기술 입력창을 찾을 수 없음");
                return false;
            }

            skillInput.first().click();
            skillInput.first().fill("");
            pm.randomDelay(100, 200);
            skillInput.first().fill(skillName);
            pm.randomDelay(300, 500);

            // "등록" 버튼 클릭
            Locator registerBtn = page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("등록").setExact(true));
            if (registerBtn.count() > 0 && registerBtn.first().isEnabled()) {
                registerBtn.first().click();
                pm.randomDelay(500, 800);
                log.info("[LinkareerResume] 스킬 등록 완료: {}", skillName);
                return true;
            }

            // 폴백: 버튼 텍스트 기반
            Locator fallbackBtn = page.locator("button:has-text('등록')");
            if (fallbackBtn.count() > 0 && fallbackBtn.first().isEnabled()) {
                fallbackBtn.first().click();
                pm.randomDelay(500, 800);
                log.info("[LinkareerResume] 스킬 등록 완료 (폴백): {}", skillName);
                return true;
            }

            log.warn("[LinkareerResume] 등록 버튼이 비활성 상태: {}", skillName);
            return false;

        } catch (Exception e) {
            log.warn("[LinkareerResume] 스킬 추가 실패 ({}): {}", skillName, e.getMessage());
            return false;
        }
    }

    // ══════════════════════════════════════════════
    //  학력
    // ══════════════════════════════════════════════

    private void fillEducations(Page page, PlaywrightManager pm, Resume resume,
                                ResumeSyncResult.Builder result) {
        try {
            List<ResumeEducation> educations = resume.getEducations();
            if (educations == null || educations.isEmpty()) {
                result.addSectionResult("학력", true, "스킵 (데이터 없음)");
                return;
            }

            scrollToHeading(page, "학력", pm);

            for (int i = 0; i < educations.size(); i++) {
                ResumeEducation edu = educations.get(i);

                // 추가 학력은 "플러스 학력 추가" 버튼 클릭
                if (i > 0) {
                    clickButtonContainingText(page, "학력 추가", pm);
                }

                fillSingleEducation(page, pm, edu);
            }

            result.addSectionSuccess("학력");
            log.info("[LinkareerResume] 학력 입력 완료 ({}건)", educations.size());

        } catch (Exception e) {
            log.error("[LinkareerResume] 학력 입력 실패: {}", e.getMessage(), e);
            result.addSectionFail("학력", e.getMessage());
        }
    }

    private void fillSingleEducation(Page page, PlaywrightManager pm, ResumeEducation edu) {
        // 학교구분 선택 (StaticText 형태이므로 클릭하여 드롭다운 열고 선택)
        if (edu.getSchoolType() != null) {
            String schoolTypeLabel = SCHOOL_TYPE_LABEL_MAP.getOrDefault(
                    edu.getSchoolType(), "대학교(4년)");
            selectSchoolType(page, schoolTypeLabel, pm);
        }

        // 학교명
        fillTextboxByLabel(page, "학교명", edu.getSchoolName(), pm);

        // 입학년월 (DB: "2013-03-01" -> 링커리어: "2013.03")
        String startDate = toLinkareerYearMonth(edu.getStartDate());
        if (startDate != null) {
            fillDateTextbox(page, "입학", startDate, pm);
        }

        // 졸업년월
        String endDate = toLinkareerYearMonth(edu.getEndDate());
        if (endDate != null) {
            fillDateTextbox(page, "졸업", endDate, pm);
        }

        // 전공
        fillTextboxByLabel(page, "전공", edu.getMajor(), pm);

        // 추가전공
        if (edu.getSubMajor() != null && !edu.getSubMajor().isBlank()) {
            fillTextboxByLabel(page, "추가전공", edu.getSubMajor(), pm);
        }

        // 학점
        if (edu.getGpa() != null && !edu.getGpa().isBlank()) {
            fillGpaTextbox(page, edu.getGpa(), pm);
        }
    }

    /**
     * 학교구분 StaticText 영역을 클릭하여 드롭다운을 열고 해당 항목을 선택한다.
     */
    private void selectSchoolType(Page page, String schoolTypeLabel, PlaywrightManager pm) {
        try {
            // "학교 구분" 텍스트 근처의 선택 요소 클릭
            Locator schoolTypeArea = page.getByText("학교 구분");
            if (schoolTypeArea.count() > 0) {
                schoolTypeArea.first().click();
                pm.randomDelay(300, 500);

                // 드롭다운 항목 클릭
                Locator option = page.getByText(schoolTypeLabel, new Page.GetByTextOptions().setExact(true));
                if (option.count() > 0) {
                    option.first().click();
                    pm.randomDelay(300, 500);
                    return;
                }
            }

            // 폴백: 직접 옵션 텍스트 클릭
            Locator fallback = page.getByRole(AriaRole.OPTION,
                    new Page.GetByRoleOptions().setName(schoolTypeLabel));
            if (fallback.count() > 0) {
                fallback.first().click();
                pm.randomDelay(300, 500);
            }

        } catch (Exception e) {
            log.warn("[LinkareerResume] 학교구분 선택 실패 ({}): {}", schoolTypeLabel, e.getMessage());
        }
    }

    // ══════════════════════════════════════════════
    //  경력
    // ══════════════════════════════════════════════

    private void fillCareers(Page page, PlaywrightManager pm, Resume resume,
                             ResumeSyncResult.Builder result) {
        try {
            List<ResumeCareer> careers = resume.getCareers();
            if (careers == null || careers.isEmpty()) {
                result.addSectionResult("경력", true, "스킵 (데이터 없음)");
                return;
            }

            // 경력 섹션이 사이드바에서 추가해야 할 수 있음
            ensureSectionVisible(page, "경력", pm);
            scrollToHeading(page, "경력", pm);

            for (int i = 0; i < careers.size(); i++) {
                ResumeCareer career = careers.get(i);

                if (i > 0) {
                    clickButtonContainingText(page, "경력 추가", pm);
                }

                fillSingleCareer(page, pm, career);
            }

            result.addSectionSuccess("경력");
            log.info("[LinkareerResume] 경력 입력 완료 ({}건)", careers.size());

        } catch (Exception e) {
            log.error("[LinkareerResume] 경력 입력 실패: {}", e.getMessage(), e);
            result.addSectionFail("경력", e.getMessage());
        }
    }

    private void fillSingleCareer(Page page, PlaywrightManager pm, ResumeCareer career) {
        fillTextboxByLabel(page, "회사명", career.getCompanyName(), pm);

        if (career.getDepartment() != null && !career.getDepartment().isBlank()) {
            fillTextboxByLabel(page, "부서명", career.getDepartment(), pm);
        }

        if (career.getPosition() != null && !career.getPosition().isBlank()) {
            fillTextboxByLabel(page, "직위", career.getPosition(), pm);
        }

        String startDate = toLinkareerYearMonth(career.getStartDate());
        if (startDate != null) {
            fillDateTextbox(page, "입사", startDate, pm);
        }

        if (career.isCurrentlyWorking()) {
            checkCheckbox(page, "재직중", pm);
        } else {
            String endDate = toLinkareerYearMonth(career.getEndDate());
            if (endDate != null) {
                fillDateTextbox(page, "퇴사", endDate, pm);
            }
        }

        if (career.getJobDescription() != null && !career.getJobDescription().isBlank()) {
            fillTextboxByLabel(page, "담당업무", career.getJobDescription(), pm);
        }
    }

    // ══════════════════════════════════════════════
    //  활동사항
    // ══════════════════════════════════════════════

    private void fillActivities(Page page, PlaywrightManager pm, Resume resume,
                                ResumeSyncResult.Builder result) {
        try {
            List<ResumeActivity> activities = resume.getActivities();
            if (activities == null || activities.isEmpty()) {
                result.addSectionResult("활동사항", true, "스킵 (데이터 없음)");
                return;
            }

            ensureSectionVisible(page, "활동사항", pm);
            scrollToHeading(page, "활동사항", pm);

            for (int i = 0; i < activities.size(); i++) {
                ResumeActivity activity = activities.get(i);

                if (i > 0) {
                    clickButtonContainingText(page, "활동 추가", pm);
                }

                fillSingleActivity(page, pm, activity);
            }

            result.addSectionSuccess("활동사항");
            log.info("[LinkareerResume] 활동사항 입력 완료 ({}건)", activities.size());

        } catch (Exception e) {
            log.error("[LinkareerResume] 활동사항 입력 실패: {}", e.getMessage(), e);
            result.addSectionFail("활동사항", e.getMessage());
        }
    }

    private void fillSingleActivity(Page page, PlaywrightManager pm, ResumeActivity activity) {
        if (activity.getActivityName() != null && !activity.getActivityName().isBlank()) {
            fillTextboxByLabel(page, "활동명", activity.getActivityName(), pm);
        }

        if (activity.getOrganization() != null && !activity.getOrganization().isBlank()) {
            fillTextboxByLabel(page, "기관명", activity.getOrganization(), pm);
        }

        String startDate = toLinkareerYearMonth(activity.getStartDate());
        if (startDate != null) {
            fillDateTextbox(page, "시작", startDate, pm);
        }

        String endDate = toLinkareerYearMonth(activity.getEndDate());
        if (endDate != null) {
            fillDateTextbox(page, "종료", endDate, pm);
        }

        if (activity.getDescription() != null && !activity.getDescription().isBlank()) {
            fillTextboxByLabel(page, "활동내용", activity.getDescription(), pm);
        }
    }

    // ══════════════════════════════════════════════
    //  자기소개서
    // ══════════════════════════════════════════════

    private void fillSelfIntroduction(Page page, PlaywrightManager pm, Resume resume,
                                      ResumeSyncResult.Builder result) {
        try {
            String selfIntro = resume.getSelfIntroduction();
            if (selfIntro == null || selfIntro.isBlank()) {
                result.addSectionResult("자기소개서", true, "스킵 (데이터 없음)");
                return;
            }

            // 사이드바에서 자기소개서 섹션 추가
            ensureSectionVisible(page, "자기소개서", pm);
            scrollToHeading(page, "자기소개서", pm);

            // textarea 찾기 - 자기소개서 heading 아래의 textarea
            Locator textarea = page.locator("textarea");
            if (textarea.count() > 0) {
                // 가장 마지막(자기소개서 영역) textarea 사용
                textarea.last().scrollIntoViewIfNeeded();
                pm.randomDelay(200, 400);
                textarea.last().click();
                textarea.last().fill("");
                pm.randomDelay(100, 200);
                textarea.last().fill(selfIntro);
                pm.randomDelay(500, 800);
            } else {
                // JS 폴백: 자기소개서 섹션 내의 textarea 또는 contenteditable 영역
                page.evaluate("(text) => {" +
                        "  const headings = document.querySelectorAll('h1, h2, h3, h4, h5, h6');" +
                        "  for (const h of headings) {" +
                        "    if (h.textContent.includes('자기소개서')) {" +
                        "      const section = h.closest('section') || h.parentElement;" +
                        "      const ta = section.querySelector('textarea');" +
                        "      if (ta) {" +
                        "        ta.value = text;" +
                        "        ta.dispatchEvent(new Event('input', {bubbles:true}));" +
                        "        ta.dispatchEvent(new Event('change', {bubbles:true}));" +
                        "        return;" +
                        "      }" +
                        "      const editable = section.querySelector('[contenteditable]');" +
                        "      if (editable) {" +
                        "        editable.innerText = text;" +
                        "        editable.dispatchEvent(new Event('input', {bubbles:true}));" +
                        "        return;" +
                        "      }" +
                        "    }" +
                        "  }" +
                        "}", selfIntro);
                pm.randomDelay(500, 800);
            }

            result.addSectionSuccess("자기소개서");
            log.info("[LinkareerResume] 자기소개서 입력 완료");

        } catch (Exception e) {
            log.error("[LinkareerResume] 자기소개서 입력 실패: {}", e.getMessage(), e);
            result.addSectionFail("자기소개서", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════
    //  최종 저장
    // ══════════════════════════════════════════════

    private void saveResume(Page page, PlaywrightManager pm, ResumeSyncResult.Builder result) {
        try {
            Locator saveBtn = page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("저장하기"));

            if (saveBtn.count() == 0) {
                // 폴백: 텍스트 기반
                saveBtn = page.locator("button:has-text('저장하기'), button:has-text('저장')");
            }

            if (saveBtn.count() == 0) {
                log.error("[LinkareerResume] 저장하기 버튼을 찾을 수 없음");
                result.addSectionFail("최종저장", "저장하기 버튼 없음");
                return;
            }

            saveBtn.first().scrollIntoViewIfNeeded();
            pm.randomDelay(500, 1000);
            saveBtn.first().click();
            log.info("[LinkareerResume] 저장하기 버튼 클릭");

            pm.longDelay();

            // 저장 결과 확인
            try {
                String finalUrl = page.url();
                log.info("[LinkareerResume] 저장 후 URL: {}", finalUrl);

                if (isLoginRedirect(finalUrl)) {
                    log.error("[LinkareerResume] 저장 후 세션 만료 감지");
                    result.addSectionFail("최종저장", "세션 만료로 저장 실패");
                    return;
                }

                // 에러 메시지 확인
                Locator errorMsg = page.locator(
                        ".error, [class*=error], [class*=alert], [role=alert]");
                if (errorMsg.count() > 0 && errorMsg.first().isVisible()) {
                    String errText = errorMsg.first().textContent();
                    log.error("[LinkareerResume] 저장 실패 - 에러: {}", errText);
                    result.addSectionFail("최종저장", "링커리어 에러: " + errText);
                } else {
                    result.addSectionSuccess("최종저장");
                    log.info("[LinkareerResume] 이력서 저장 성공");
                }

            } catch (Exception pageEx) {
                // 페이지 전환으로 컨텍스트 종료 = 저장 성공 간주
                result.addSectionSuccess("최종저장");
                log.info("[LinkareerResume] 이력서 저장 성공 (페이지 전환으로 컨텍스트 종료)");
            }

        } catch (Exception e) {
            log.error("[LinkareerResume] 최종 저장 실패: {}", e.getMessage(), e);
            result.addSectionFail("최종저장", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════
    //  헬퍼 메서드
    // ══════════════════════════════════════════════

    /**
     * 로그인 리다이렉트 여부를 확인한다.
     */
    private boolean isLoginRedirect(String url) {
        return url.contains("/login") || url.contains("/signin");
    }

    /**
     * confirm/alert 다이얼로그를 자동 수락하도록 오버라이드한다.
     */
    private void overrideDialogs(Page page) {
        page.evaluate("() => {" +
                "  window.confirm = function() { return true; };" +
                "  window.alert = function() { return; };" +
                "}");
    }

    /**
     * 사이드바에서 섹션이 표시되어 있는지 확인하고, 없으면 "섹션 추가 아이콘" 버튼을 클릭하여 추가한다.
     */
    private void ensureSectionVisible(Page page, String sectionName, PlaywrightManager pm) {
        try {
            // 이미 heading이 보이면 추가 불필요
            Locator heading = page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(sectionName));
            if (heading.count() > 0 && heading.first().isVisible()) {
                return;
            }

            // 사이드바에서 해당 섹션명 옆의 "섹션 추가 아이콘" 버튼 클릭
            // 사이드바 텍스트를 찾고 인접한 추가 버튼 클릭
            Locator sidebarItem = page.getByText(sectionName);
            if (sidebarItem.count() > 0) {
                // 섹션명 텍스트 근처의 "섹션 추가 아이콘" 버튼 찾기
                Locator addIcon = sidebarItem.first()
                        .locator("xpath=./ancestor::*[position()<=3]")
                        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("섹션 추가 아이콘"));

                if (addIcon.count() > 0) {
                    addIcon.first().click();
                    pm.shortDelay();
                    log.info("[LinkareerResume] 섹션 추가 완료: {}", sectionName);
                    return;
                }

                // 폴백: 해당 텍스트의 부모에서 button 찾기
                Locator parentBtn = sidebarItem.first()
                        .locator("xpath=./ancestor::*[position()<=3]")
                        .locator("button");
                if (parentBtn.count() > 0) {
                    parentBtn.first().click();
                    pm.shortDelay();
                    log.info("[LinkareerResume] 섹션 추가 완료 (폴백): {}", sectionName);
                    return;
                }
            }

            // JS 폴백: 사이드바에서 섹션명을 포함하는 요소의 인접 버튼 클릭
            page.evaluate("(name) => {" +
                    "  const items = document.querySelectorAll('li, div, span');" +
                    "  for (const item of items) {" +
                    "    if (item.textContent.trim() === name || item.textContent.trim().includes(name)) {" +
                    "      const btn = item.querySelector('button') || item.parentElement.querySelector('button');" +
                    "      if (btn) { btn.click(); return; }" +
                    "    }" +
                    "  }" +
                    "}", sectionName);
            pm.shortDelay();

        } catch (Exception e) {
            log.warn("[LinkareerResume] 섹션 추가 시도 실패 ({}): {}", sectionName, e.getMessage());
        }
    }

    /**
     * heading 텍스트로 해당 섹션까지 스크롤한다.
     */
    private void scrollToHeading(Page page, String headingText, PlaywrightManager pm) {
        try {
            Locator heading = page.getByRole(AriaRole.HEADING,
                    new Page.GetByRoleOptions().setName(headingText));
            if (heading.count() > 0) {
                heading.first().scrollIntoViewIfNeeded();
                pm.randomDelay(300, 500);
            }
        } catch (Exception e) {
            log.warn("[LinkareerResume] 헤딩 스크롤 실패 ({}): {}", headingText, e.getMessage());
        }
    }

    /**
     * label 텍스트로 textbox를 찾아 값을 입력한다.
     * 링커리어는 표준 label이 아닌 StaticText로 레이블을 사용하므로,
     * getByRole TEXTBOX + name(label 텍스트) 조합으로 찾는다.
     */
    private void fillTextboxByLabel(Page page, String label, String value,
                                    PlaywrightManager pm) {
        if (value == null || value.isBlank()) return;

        try {
            Locator input = page.getByRole(AriaRole.TEXTBOX,
                    new Page.GetByRoleOptions().setName(label));

            if (input.count() > 0) {
                input.first().click();
                input.first().fill("");
                pm.randomDelay(100, 200);
                input.first().fill(value);
                pm.randomDelay(300, 500);
                return;
            }

            // 폴백: getByLabel
            Locator labelInput = page.getByLabel(label);
            if (labelInput.count() > 0) {
                labelInput.first().click();
                labelInput.first().fill("");
                pm.randomDelay(100, 200);
                labelInput.first().fill(value);
                pm.randomDelay(300, 500);
                return;
            }

            // JS 폴백: StaticText 근처의 input 찾기
            page.evaluate("(args) => {" +
                    "  const label = args[0];" +
                    "  const val = args[1];" +
                    "  const allText = document.querySelectorAll('span, label, p, div');" +
                    "  for (const el of allText) {" +
                    "    if (el.childNodes.length === 1 && el.textContent.trim() === label) {" +
                    "      const parent = el.closest('div[class], section, fieldset') || el.parentElement;" +
                    "      const input = parent.querySelector('input[type=text], input:not([type]), textarea');" +
                    "      if (input) {" +
                    "        input.focus();" +
                    "        input.value = val;" +
                    "        input.dispatchEvent(new Event('input', {bubbles:true}));" +
                    "        input.dispatchEvent(new Event('change', {bubbles:true}));" +
                    "        return;" +
                    "      }" +
                    "    }" +
                    "  }" +
                    "}", new Object[]{label, value});
            pm.randomDelay(300, 500);

        } catch (Exception e) {
            log.warn("[LinkareerResume] '{}' 입력 실패: {}", label, e.getMessage());
        }
    }

    /**
     * 전화번호 textbox를 찾아 입력한다.
     * 링커리어 전화번호 필드는 label이 없을 수 있으므로 값 패턴(010-)으로 식별한다.
     */
    private void fillPhoneTextbox(Page page, String phone, PlaywrightManager pm) {
        if (phone == null || phone.isBlank()) return;

        try {
            // 전화번호 textbox는 보통 010으로 시작하는 값이 있거나, type=tel
            Locator telInput = page.locator("input[type='tel']");
            if (telInput.count() > 0) {
                telInput.first().click();
                telInput.first().fill("");
                pm.randomDelay(100, 200);
                telInput.first().fill(phone);
                pm.randomDelay(300, 500);
                return;
            }

            // getByRole로 전화번호 패턴 시도
            Locator phoneInput = page.getByRole(AriaRole.TEXTBOX,
                    new Page.GetByRoleOptions().setName("전화"));
            if (phoneInput.count() == 0) {
                phoneInput = page.getByRole(AriaRole.TEXTBOX,
                        new Page.GetByRoleOptions().setName("휴대폰"));
            }
            if (phoneInput.count() == 0) {
                phoneInput = page.getByRole(AriaRole.TEXTBOX,
                        new Page.GetByRoleOptions().setName("연락처"));
            }

            if (phoneInput.count() > 0) {
                phoneInput.first().click();
                phoneInput.first().fill("");
                pm.randomDelay(100, 200);
                phoneInput.first().fill(phone);
                pm.randomDelay(300, 500);
            }

        } catch (Exception e) {
            log.warn("[LinkareerResume] 전화번호 입력 실패: {}", e.getMessage());
        }
    }

    /**
     * 날짜 관련 textbox를 찾아 입력한다.
     * 링커리어는 "입학년월", "졸업년월" 등의 label 대신 텍스트 근처 input을 사용한다.
     */
    private void fillDateTextbox(Page page, String dateLabel, String dateValue,
                                 PlaywrightManager pm) {
        if (dateValue == null || dateValue.isBlank()) return;

        try {
            // label에 해당 텍스트를 포함하는 textbox 찾기
            Locator input = page.getByRole(AriaRole.TEXTBOX,
                    new Page.GetByRoleOptions().setName(dateLabel));

            if (input.count() > 0) {
                input.first().click();
                input.first().fill("");
                pm.randomDelay(100, 200);
                input.first().fill(dateValue);
                pm.randomDelay(300, 500);
                return;
            }

            // 폴백: "년월" 포함 label 시도
            Locator yearMonthInput = page.getByRole(AriaRole.TEXTBOX,
                    new Page.GetByRoleOptions().setName(dateLabel + "년월"));
            if (yearMonthInput.count() > 0) {
                yearMonthInput.first().click();
                yearMonthInput.first().fill("");
                pm.randomDelay(100, 200);
                yearMonthInput.first().fill(dateValue);
                pm.randomDelay(300, 500);
            }

        } catch (Exception e) {
            log.warn("[LinkareerResume] 날짜 '{}' 입력 실패: {}", dateLabel, e.getMessage());
        }
    }

    /**
     * 학점 textbox를 찾아 입력한다.
     */
    private void fillGpaTextbox(Page page, String gpa, PlaywrightManager pm) {
        try {
            Locator gpaInput = page.getByRole(AriaRole.TEXTBOX,
                    new Page.GetByRoleOptions().setName("학점"));
            if (gpaInput.count() > 0) {
                gpaInput.first().click();
                gpaInput.first().fill("");
                pm.randomDelay(100, 200);
                gpaInput.first().fill(gpa);
                pm.randomDelay(300, 500);
                return;
            }

            // 폴백: placeholder 기반
            Locator placeholderInput = page.getByPlaceholder("학점");
            if (placeholderInput.count() > 0) {
                placeholderInput.first().click();
                placeholderInput.first().fill("");
                pm.randomDelay(100, 200);
                placeholderInput.first().fill(gpa);
                pm.randomDelay(300, 500);
            }

        } catch (Exception e) {
            log.warn("[LinkareerResume] 학점 입력 실패: {}", e.getMessage());
        }
    }

    /**
     * 체크박스를 label 텍스트로 찾아 체크한다.
     */
    private void checkCheckbox(Page page, String label, PlaywrightManager pm) {
        try {
            Locator checkbox = page.getByRole(AriaRole.CHECKBOX,
                    new Page.GetByRoleOptions().setName(label));

            if (checkbox.count() > 0) {
                if (!checkbox.first().isChecked()) {
                    checkbox.first().check();
                    pm.randomDelay(300, 500);
                }
                return;
            }

            // 폴백: label 텍스트 클릭
            Locator labelLocator = page.getByText(label);
            if (labelLocator.count() > 0) {
                labelLocator.first().click();
                pm.randomDelay(300, 500);
            }

        } catch (Exception e) {
            log.warn("[LinkareerResume] 체크박스 '{}' 체크 실패: {}", label, e.getMessage());
        }
    }

    /**
     * 텍스트를 포함하는 버튼을 찾아 클릭한다 ("플러스 학력 추가", "경력 추가" 등).
     */
    private void clickButtonContainingText(Page page, String buttonText, PlaywrightManager pm) {
        try {
            Locator btn = page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName(buttonText));

            if (btn.count() > 0) {
                btn.first().scrollIntoViewIfNeeded();
                pm.randomDelay(200, 400);
                btn.first().click();
                pm.shortDelay();
                return;
            }

            // 폴백: 텍스트 포함 버튼
            Locator fallback = page.locator("button:has-text('" + buttonText + "')");
            if (fallback.count() > 0) {
                fallback.first().scrollIntoViewIfNeeded();
                pm.randomDelay(200, 400);
                fallback.first().click();
                pm.shortDelay();
            }

        } catch (Exception e) {
            log.warn("[LinkareerResume] 버튼 '{}' 클릭 실패: {}", buttonText, e.getMessage());
        }
    }

    /**
     * DB 날짜 형식을 링커리어 년월 형식으로 변환한다.
     * "2013-03-01" 또는 "201303" -> "2013.03"
     */
    private String toLinkareerYearMonth(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;

        String cleaned = dateStr.replace("-", "").replace(".", "").replace("/", "");
        if (cleaned.length() < 6) return null;

        String year = cleaned.substring(0, 4);
        String month = cleaned.substring(4, 6);
        return year + "." + month;
    }

    /**
     * DB 생년월일 형식을 링커리어 형식으로 변환한다.
     * "1994-05-23" 또는 "19940523" -> "1994.05.23"
     */
    private String toLinkareerBirthDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;

        String cleaned = dateStr.replace("-", "").replace(".", "").replace("/", "");
        if (cleaned.length() < 8) return null;

        String year = cleaned.substring(0, 4);
        String month = cleaned.substring(4, 6);
        String day = cleaned.substring(6, 8);
        return year + "." + month + "." + day;
    }
}
