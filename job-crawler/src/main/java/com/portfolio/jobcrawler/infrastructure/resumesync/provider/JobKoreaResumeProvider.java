package com.portfolio.jobcrawler.infrastructure.resumesync.provider;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.portfolio.jobcrawler.application.ai.dto.CoverLetterSection;
import com.portfolio.jobcrawler.domain.resume.entity.*;
import com.portfolio.jobcrawler.domain.resume.vo.GraduationStatus;
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
 * 잡코리아 이력서 동기화 구현체.
 * 잡코리아 이력서 작성 페이지(https://www.jobkorea.co.kr/User/Resume/Write)의
 * 실제 HTML 구조에 맞춘 셀렉터를 사용한다.
 *
 * 핵심 규칙:
 * - Playwright의 getByRole/getByPlaceholder/getByLabel 텍스트 기반 로케이터 사용
 * - 기존 데이터가 있으면 clear 후 입력
 * - 각 섹션별 SRP 준수 (메서드 분리)
 */
@Slf4j
@Component
public class JobKoreaResumeProvider implements ResumeProvider {

    private static final String SITE_NAME = "JOBKOREA";
    private static final String RESUME_WRITE_URL =
            "https://www.jobkorea.co.kr/User/Resume/Write";

    private static final int TYPE_DELAY_MS = 80;

    /** 잡코리아 학교구분 버튼 텍스트 매핑 */
    private static final Map<SchoolType, String> SCHOOL_TYPE_LABEL_MAP = Map.of(
            SchoolType.HIGH_SCHOOL, "고등학교",
            SchoolType.COLLEGE_2Y, "대학교(2,3년)",
            SchoolType.COLLEGE_4Y, "대학교(4년)",
            SchoolType.GRADUATE_MASTER, "대학원(석사)",
            SchoolType.GRADUATE_DOCTOR, "대학원(박사)"
    );

    /** 잡코리아 졸업상태 버튼 텍스트 매핑 */
    private static final Map<GraduationStatus, String> GRADUATION_LABEL_MAP = Map.of(
            GraduationStatus.GRADUATED, "졸업",
            GraduationStatus.ENROLLED, "재학중",
            GraduationStatus.LEAVE_OF_ABSENCE, "휴학중",
            GraduationStatus.COMPLETED, "수료",
            GraduationStatus.DROPPED, "중퇴",
            GraduationStatus.EXPECTED, "졸업예정"
    );

    @Override
    public String getSiteName() {
        return SITE_NAME;
    }

    @Override
    public ResumeSyncResult syncResume(Page page, PlaywrightManager pm, Resume resume) {
        log.info("[JobKoreaResume] 이력서 동기화 시작");

        try {
            // beforeunload 다이얼로그 자동 수락
            page.onDialog(dialog -> {
                log.info("[JobKoreaResume] 다이얼로그 자동 수락: {}", dialog.message());
                dialog.accept();
            });

            page.navigate(RESUME_WRITE_URL,
                    new Page.NavigateOptions().setWaitUntil(
                            com.microsoft.playwright.options.WaitUntilState.LOAD));
            pm.shortDelay();

            String currentUrl = page.url();
            if (isLoginRedirect(currentUrl)) {
                log.error("[JobKoreaResume] 로그인 세션 만료: {}", currentUrl);
                return ResumeSyncResult.sessionExpired(
                        "잡코리아 로그인 세션이 만료되었습니다. 설정에서 다시 연동해주세요.");
            }

            log.info("[JobKoreaResume] 이력서 작성 페이지 로드 완료: {}", currentUrl);

            // confirm/alert 자동 수락 오버라이드
            overrideDialogs(page);

            ResumeSyncResult.Builder result = ResumeSyncResult.builder();

            fillBasicInfo(page, pm, resume, result);
            fillEducations(page, pm, resume, result);
            fillCareers(page, pm, resume, result);
            fillSkills(page, pm, resume, result);
            fillSelfIntroduction(page, pm, resume, result);
            saveResume(page, pm, result);

            return result.build();

        } catch (Exception e) {
            log.error("[JobKoreaResume] 동기화 실패: {}", e.getMessage(), e);
            return ResumeSyncResult.fail(e.getMessage());
        }
    }

    @Override
    public ResumeSyncResult updateSelfIntroduction(Page page, PlaywrightManager pm,
                                                   List<CoverLetterSection> sections) {
        log.info("[JobKoreaResume] 자기소개서 업데이트 시작 ({}개 문항)", sections.size());

        try {
            // beforeunload 다이얼로그 자동 수락
            page.onDialog(dialog -> {
                log.info("[JobKoreaResume] 다이얼로그 자동 수락: {}", dialog.message());
                dialog.accept();
            });

            page.navigate(RESUME_WRITE_URL,
                    new Page.NavigateOptions().setWaitUntil(
                            com.microsoft.playwright.options.WaitUntilState.LOAD));
            pm.shortDelay();

            String currentUrl = page.url();
            if (isLoginRedirect(currentUrl)) {
                log.error("[JobKoreaResume] 로그인 세션 만료: {}", currentUrl);
                return ResumeSyncResult.sessionExpired(
                        "잡코리아 로그인 세션이 만료되었습니다. 설정에서 다시 연동해주세요.");
            }

            log.info("[JobKoreaResume] 이력서 작성 페이지 로드 완료: {}", currentUrl);

            // confirm/alert 자동 수락 오버라이드
            overrideDialogs(page);

            // "필드추가" 버튼을 클릭하여 사이드 메뉴 열기
            clickFieldAddMenuButton(page, pm);

            // 사이드 메뉴에서 "자기소개서" 항목 클릭하여 섹션 활성화
            clickSideMenuItem(page, "자기소개서", pm);

            // 기존 자기소개서 단락 모두 삭제
            deleteAllCoverLetterParagraphs(page, pm);

            // 자기소개서 heading으로 스크롤
            Locator heading = page.getByRole(
                    com.microsoft.playwright.options.AriaRole.HEADING,
                    new Page.GetByRoleOptions().setName("자기소개서"));
            if (heading.count() > 0) {
                heading.first().scrollIntoViewIfNeeded();
                pm.shortDelay();
            }

            // 각 문항 입력
            for (int i = 0; i < sections.size(); i++) {
                CoverLetterSection section = sections.get(i);

                // 첫 번째 문항은 기본 폼이 있으므로 추가 불필요, 두 번째부터 "추가" 버튼 클릭
                if (i > 0) {
                    clickCoverLetterAddButton(page, pm);
                }

                // 제목 입력 (getByRole TEXTBOX + "제목")
                Locator titleInputs = page.getByRole(
                        com.microsoft.playwright.options.AriaRole.TEXTBOX,
                        new Page.GetByRoleOptions().setName("제목"));
                if (titleInputs.count() > i) {
                    titleInputs.nth(i).click();
                    titleInputs.nth(i).fill("");
                    pm.randomDelay(100, 200);
                    titleInputs.nth(i).fill(section.title());
                    pm.randomDelay(300, 500);
                }

                // 내용 입력 (getByRole TEXTBOX + "해당내용을 입력하세요.")
                Locator contentTextareas = page.getByPlaceholder("해당내용을 입력하세요.");
                if (contentTextareas.count() > i) {
                    contentTextareas.nth(i).click();
                    contentTextareas.nth(i).fill("");
                    pm.randomDelay(100, 200);
                    contentTextareas.nth(i).fill(section.content());
                    pm.randomDelay(300, 500);
                }

                log.info("[JobKoreaResume] 자기소개서 문항 입력 완료: {}", section.title());
            }

            // 필수 동의 체크 + 이력서 저장
            agreeToTerms(page, pm);

            Locator saveBtn = page.getByRole(
                    com.microsoft.playwright.options.AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("이력서저장"));
            if (saveBtn.count() == 0) {
                saveBtn = page.locator("button:has-text('이력서저장'), button:has-text('저장')");
            }
            if (saveBtn.count() > 0) {
                saveBtn.first().scrollIntoViewIfNeeded();
                pm.randomDelay(500, 1000);
                saveBtn.first().click();
                pm.longDelay();
            }

            log.info("[JobKoreaResume] 자기소개서 업데이트 완료 ({}개 문항)", sections.size());
            return ResumeSyncResult.success();

        } catch (Exception e) {
            log.error("[JobKoreaResume] 자기소개서 업데이트 실패: {}", e.getMessage(), e);
            return ResumeSyncResult.fail(e.getMessage());
        }
    }

    /**
     * 자기소개서 영역의 기존 단락을 모두 삭제한다.
     */
    private void deleteAllCoverLetterParagraphs(Page page, PlaywrightManager pm) {
        for (int i = 0; i < 20; i++) {
            Locator deleteBtn = page.getByRole(
                    com.microsoft.playwright.options.AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("단락삭제"));
            if (deleteBtn.count() == 0) break;
            deleteBtn.first().click();
            pm.randomDelay(500, 800);
        }
        pm.shortDelay();
    }

    /**
     * 자기소개서 영역의 "추가" 버튼을 클릭한다.
     */
    private void clickCoverLetterAddButton(Page page, PlaywrightManager pm) {
        try {
            // 자기소개서 heading 근처의 "추가" 버튼 클릭
            Object clicked = page.evaluate("() => {" +
                    "  const headings = document.querySelectorAll('h2, h3');" +
                    "  for (const h of headings) {" +
                    "    if (h.textContent.includes('자기소개서')) {" +
                    "      const section = h.closest('section') || h.closest('[class*=section]')" +
                    "        || h.parentElement?.parentElement;" +
                    "      if (section) {" +
                    "        const btns = section.querySelectorAll('button');" +
                    "        for (const btn of btns) {" +
                    "          if (btn.textContent.trim() === '추가') {" +
                    "            btn.scrollIntoView({behavior:'instant', block:'center'});" +
                    "            btn.click();" +
                    "            return true;" +
                    "          }" +
                    "        }" +
                    "      }" +
                    "    }" +
                    "  }" +
                    "  return false;" +
                    "}");

            if (!Boolean.TRUE.equals(clicked)) {
                // 폴백: getByRole
                clickButtonByText(page, "추가", pm);
            }

            pm.shortDelay();
        } catch (Exception e) {
            log.warn("[JobKoreaResume] 자기소개서 추가 버튼 클릭 실패: {}", e.getMessage());
        }
    }

    // ── 인적사항 ──

    private void fillBasicInfo(Page page, PlaywrightManager pm, Resume resume,
                               ResumeSyncResult.Builder result) {
        try {
            // 이름 (이미 pre-filled 되어 있을 수 있지만 갱신)
            fillTextboxByLabel(page, "이름", resume.getName(), pm);

            // 이메일
            fillTextboxByLabel(page, "이메일", resume.getEmail(), pm);

            // 휴대폰번호
            fillTextboxByLabel(page, "휴대폰번호", resume.getPhone(), pm);

            result.addSectionSuccess("인적사항");
            log.info("[JobKoreaResume] 인적사항 입력 완료");

        } catch (Exception e) {
            log.error("[JobKoreaResume] 인적사항 입력 실패: {}", e.getMessage(), e);
            result.addSectionFail("인적사항", e.getMessage());
        }
    }

    // ── 학력 ──

    private void fillEducations(Page page, PlaywrightManager pm, Resume resume,
                                ResumeSyncResult.Builder result) {
        try {
            List<ResumeEducation> educations = resume.getEducations();
            if (educations == null || educations.isEmpty()) {
                result.addSectionResult("학력", true, "스킵 (데이터 없음)");
                return;
            }

            for (int i = 0; i < educations.size(); i++) {
                ResumeEducation edu = educations.get(i);

                // 첫 번째 항목은 기본 폼이 열려있으므로 추가 버튼 불필요
                if (i > 0) {
                    clickButtonByText(page, "추가", pm);
                }

                fillSingleEducation(page, pm, edu);
            }

            result.addSectionSuccess("학력");
            log.info("[JobKoreaResume] 학력 입력 완료 ({}건)", educations.size());

        } catch (Exception e) {
            log.error("[JobKoreaResume] 학력 입력 실패: {}", e.getMessage(), e);
            result.addSectionFail("학력", e.getMessage());
        }
    }

    private void fillSingleEducation(Page page, PlaywrightManager pm, ResumeEducation edu) {
        // 학교구분 버튼 클릭 (대학교(4년) 등)
        if (edu.getSchoolType() != null) {
            String label = SCHOOL_TYPE_LABEL_MAP.getOrDefault(edu.getSchoolType(), "대학교(4년)");
            clickToggleButton(page, label, pm);
        }

        // 학교명
        fillTextboxByLabel(page, "학교명", edu.getSchoolName(), pm);

        // 입학년월 (DB: "2013-03-01" 또는 "201303" → 잡코리아: "2013.03")
        String startDate = toJobKoreaYearMonth(edu.getStartDate());
        if (startDate != null) {
            fillTextboxByLabel(page, "입학년월", startDate, pm);
        }

        // 졸업년월
        String endDate = toJobKoreaYearMonth(edu.getEndDate());
        if (endDate != null) {
            fillTextboxByLabel(page, "졸업년월", endDate, pm);
        }

        // 졸업상태 버튼 클릭
        if (edu.getGraduationStatus() != null) {
            String gradLabel = GRADUATION_LABEL_MAP.getOrDefault(
                    edu.getGraduationStatus(), "졸업");
            clickToggleButton(page, gradLabel, pm);
        }

        // 전공명
        fillTextboxByLabel(page, "전공명", edu.getMajor(), pm);

        // 학점
        if (edu.getGpa() != null && !edu.getGpa().isBlank()) {
            fillTextboxByLabel(page, "학점", edu.getGpa(), pm);

            // 총점 버튼 (4.5 또는 4.3)
            if (edu.getGpaScale() != null && !edu.getGpaScale().isBlank()) {
                clickToggleButton(page, edu.getGpaScale(), pm);
            }
        }
    }

    // ── 경력 ──

    private void fillCareers(Page page, PlaywrightManager pm, Resume resume,
                             ResumeSyncResult.Builder result) {
        try {
            List<ResumeCareer> careers = resume.getCareers();
            if (careers == null || careers.isEmpty()) {
                result.addSectionResult("경력", true, "스킵 (데이터 없음)");
                return;
            }

            for (int i = 0; i < careers.size(); i++) {
                ResumeCareer career = careers.get(i);

                // 첫 번째 항목은 기본 폼이 열려있을 수 있음, 추가 경력은 "추가" 버튼
                if (i > 0) {
                    clickCareerAddButton(page, pm);
                }

                fillSingleCareer(page, pm, career);
            }

            result.addSectionSuccess("경력");
            log.info("[JobKoreaResume] 경력 입력 완료 ({}건)", careers.size());

        } catch (Exception e) {
            log.error("[JobKoreaResume] 경력 입력 실패: {}", e.getMessage(), e);
            result.addSectionFail("경력", e.getMessage());
        }
    }

    private void fillSingleCareer(Page page, PlaywrightManager pm, ResumeCareer career) {
        // 회사명
        fillTextboxByLabel(page, "회사명", career.getCompanyName(), pm);

        // 부서명
        if (career.getDepartment() != null && !career.getDepartment().isBlank()) {
            fillTextboxByLabel(page, "부서명", career.getDepartment(), pm);
        }

        // 입사년월
        String startDate = toJobKoreaYearMonth(career.getStartDate());
        if (startDate != null) {
            fillTextboxByLabel(page, "입사년월", startDate, pm);
        }

        // 재직중 체크박스 또는 퇴사년월
        if (career.isCurrentlyWorking()) {
            checkCheckbox(page, "재직중", pm);
        } else {
            String endDate = toJobKoreaYearMonth(career.getEndDate());
            if (endDate != null) {
                fillTextboxByLabel(page, "퇴사년월", endDate, pm);
            }
        }

        // 연봉
        if (career.getSalary() != null && !career.getSalary().isBlank()) {
            fillTextboxByLabel(page, "연봉", career.getSalary(), pm);
        }

        // 담당업무 (placeholder 기반)
        if (career.getJobDescription() != null && !career.getJobDescription().isBlank()) {
            fillTextboxByPlaceholder(page,
                    "담당하신 업무와 성과에 대해 간단명료하게 적어주세요.",
                    career.getJobDescription(), pm);
        }
    }

    // ── 스킬 ──

    private void fillSkills(Page page, PlaywrightManager pm, Resume resume,
                            ResumeSyncResult.Builder result) {
        try {
            List<ResumeSkill> skills = resume.getSkills();
            if (skills == null || skills.isEmpty()) {
                result.addSectionResult("스킬", true, "스킵 (데이터 없음)");
                return;
            }

            int addedCount = 0;
            for (ResumeSkill skill : skills) {
                String skillName = skill.getSkillName();
                if (skillName == null || skillName.isBlank()) continue;

                if (addSingleSkill(page, pm, skillName)) {
                    addedCount++;
                }
            }

            result.addSectionResult("스킬", true,
                    String.format("%d/%d건 입력 완료", addedCount, skills.size()));
            log.info("[JobKoreaResume] 스킬 입력 완료 ({}/{}건)", addedCount, skills.size());

        } catch (Exception e) {
            log.error("[JobKoreaResume] 스킬 입력 실패: {}", e.getMessage(), e);
            result.addSectionFail("스킬", e.getMessage());
        }
    }

    /**
     * 스킬 검색창에 스킬명을 타이핑하고 검색 결과에서 선택한다.
     * 검색 결과가 없으면 직접 추가를 시도한다.
     */
    private boolean addSingleSkill(Page page, PlaywrightManager pm, String skillName) {
        try {
            // 스킬 검색 input 찾기
            Locator searchInput = page.getByPlaceholder("찾으시는 스킬이 있나요?");
            if (searchInput.count() == 0) {
                log.warn("[JobKoreaResume] 스킬 검색 input을 찾을 수 없음");
                return false;
            }

            // clear & type
            searchInput.first().click();
            searchInput.first().fill("");
            pm.randomDelay(200, 400);
            searchInput.first().type(skillName, new Locator.TypeOptions().setDelay(TYPE_DELAY_MS));
            pm.randomDelay(300, 500);

            // 검색 버튼 클릭
            Locator searchBtn = page.getByRole(
                    com.microsoft.playwright.options.AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("search"));
            if (searchBtn.count() > 0) {
                searchBtn.first().click();
                pm.randomDelay(1000, 1500);
            }

            // 검색 결과에서 정확/부분 매칭 클릭 (JS로 처리)
            Boolean selected = (Boolean) page.evaluate("(name) => {" +
                    "  const items = document.querySelectorAll('.skillList button, .skill-list button, [class*=skill] button');" +
                    "  for (const item of items) {" +
                    "    const text = item.textContent.trim();" +
                    "    if (text.toLowerCase() === name.toLowerCase()) {" +
                    "      item.click(); return true;" +
                    "    }" +
                    "  }" +
                    "  for (const item of items) {" +
                    "    const text = item.textContent.trim();" +
                    "    if (text.toLowerCase().includes(name.toLowerCase())) {" +
                    "      item.click(); return true;" +
                    "    }" +
                    "  }" +
                    "  return false;" +
                    "}", skillName);

            if (Boolean.TRUE.equals(selected)) {
                log.info("[JobKoreaResume] 스킬 선택 완료: {}", skillName);
                pm.randomDelay(500, 800);
                return true;
            }

            // 검색 결과 없으면 직접 추가 시도 (첫 번째 검색 결과 항목 클릭 폴백)
            Boolean directAdded = (Boolean) page.evaluate("(name) => {" +
                    "  const directBtn = document.querySelector('[class*=direct] button, [class*=add] button');" +
                    "  if (directBtn) { directBtn.click(); return true; }" +
                    "  return false;" +
                    "}", skillName);

            if (Boolean.TRUE.equals(directAdded)) {
                log.info("[JobKoreaResume] 스킬 직접 추가: {}", skillName);
                pm.randomDelay(500, 800);
                return true;
            }

            log.warn("[JobKoreaResume] 스킬 매칭 실패 (스킵): {}", skillName);
            return false;

        } catch (Exception e) {
            log.warn("[JobKoreaResume] 스킬 추가 실패 ({}): {}", skillName, e.getMessage());
            return false;
        }
    }

    // ── 자기소개서 ──

    private void fillSelfIntroduction(Page page, PlaywrightManager pm, Resume resume,
                                      ResumeSyncResult.Builder result) {
        try {
            String selfIntro = resume.getSelfIntroduction();
            if (selfIntro == null || selfIntro.isBlank()) {
                result.addSectionResult("자기소개서", true, "스킵 (데이터 없음)");
                return;
            }

            // "필드추가" 버튼을 클릭하여 사이드 메뉴 열기
            clickFieldAddMenuButton(page, pm);

            // 사이드 메뉴에서 "자기소개서" 항목 클릭
            clickSideMenuItem(page, "자기소개서", pm);

            // 자기소개서 textarea 입력 (placeholder나 label 기반)
            Locator selfIntroTextarea = page.locator("textarea").filter(
                    new Locator.FilterOptions().setHasText(""));
            if (selfIntroTextarea.count() == 0) {
                // 넓은 범위로 재시도: 자기소개서 영역의 textarea
                selfIntroTextarea = page.locator("[class*=intro] textarea, [class*=self] textarea, [id*=intro] textarea");
            }

            if (selfIntroTextarea.count() > 0) {
                selfIntroTextarea.last().click();
                selfIntroTextarea.last().fill(selfIntro);
                pm.randomDelay(500, 800);
            } else {
                // JS 폴백: 모든 textarea 중 자기소개서 영역의 것 찾기
                page.evaluate("(text) => {" +
                        "  const textareas = document.querySelectorAll('textarea');" +
                        "  for (const ta of textareas) {" +
                        "    const section = ta.closest('section, div[class*=intro], div[class*=self]');" +
                        "    if (section) {" +
                        "      const heading = section.querySelector('h2, h3, .title');" +
                        "      if (heading && heading.textContent.includes('자기소개')) {" +
                        "        ta.value = text;" +
                        "        ta.dispatchEvent(new Event('input', {bubbles:true}));" +
                        "        ta.dispatchEvent(new Event('change', {bubbles:true}));" +
                        "        return;" +
                        "      }" +
                        "    }" +
                        "  }" +
                        "  // 폴백: 가장 마지막 큰 textarea" +
                        "  const last = textareas[textareas.length - 1];" +
                        "  if (last) {" +
                        "    last.value = text;" +
                        "    last.dispatchEvent(new Event('input', {bubbles:true}));" +
                        "    last.dispatchEvent(new Event('change', {bubbles:true}));" +
                        "  }" +
                        "}", selfIntro);
                pm.randomDelay(500, 800);
            }

            result.addSectionSuccess("자기소개서");
            log.info("[JobKoreaResume] 자기소개서 입력 완료");

        } catch (Exception e) {
            log.error("[JobKoreaResume] 자기소개서 입력 실패: {}", e.getMessage(), e);
            result.addSectionFail("자기소개서", e.getMessage());
        }
    }

    // ── 최종 저장 ──

    private void saveResume(Page page, PlaywrightManager pm, ResumeSyncResult.Builder result) {
        try {
            // 1. 필수 동의 체크박스 체크
            agreeToTerms(page, pm);

            // 2. "이력서저장" 버튼 클릭
            Locator saveBtn = page.getByRole(
                    com.microsoft.playwright.options.AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("이력서저장"));

            if (saveBtn.count() == 0) {
                // 폴백: 텍스트로 버튼 찾기
                saveBtn = page.locator("button:has-text('이력서저장'), button:has-text('저장')");
            }

            if (saveBtn.count() == 0) {
                log.error("[JobKoreaResume] 이력서저장 버튼을 찾을 수 없음");
                result.addSectionFail("최종저장", "이력서저장 버튼 없음");
                return;
            }

            // 스크롤 후 클릭
            saveBtn.first().scrollIntoViewIfNeeded();
            pm.randomDelay(500, 1000);
            saveBtn.first().click();
            log.info("[JobKoreaResume] 이력서저장 버튼 클릭");

            pm.longDelay();

            // 3. 저장 결과 확인
            try {
                String finalUrl = page.url();
                log.info("[JobKoreaResume] 저장 후 URL: {}", finalUrl);

                if (!finalUrl.contains("Write") && !finalUrl.contains("write")) {
                    result.addSectionSuccess("최종저장");
                    log.info("[JobKoreaResume] 이력서 저장 성공 - 리다이렉트됨: {}", finalUrl);
                } else {
                    // 에러 메시지 확인
                    Locator errorMsg = page.locator(
                            ".error_message, .alert_msg, .toast, [class*=error], [class*=alert]");
                    if (errorMsg.count() > 0 && errorMsg.first().isVisible()) {
                        String errText = errorMsg.first().textContent();
                        log.error("[JobKoreaResume] 저장 실패 - 에러: {}", errText);
                        result.addSectionFail("최종저장", "잡코리아 에러: " + errText);
                    } else {
                        result.addSectionResult("최종저장", true,
                                "저장 버튼 클릭 완료, 결과 확인 필요");
                    }
                }
            } catch (Exception pageEx) {
                // 페이지 리다이렉트로 컨텍스트 종료 = 저장 성공
                result.addSectionSuccess("최종저장");
                log.info("[JobKoreaResume] 이력서 저장 성공 (페이지 리다이렉트로 컨텍스트 종료)");
            }

        } catch (Exception e) {
            log.error("[JobKoreaResume] 최종 저장 실패: {}", e.getMessage(), e);
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
        return url.contains("/login") || url.contains("/Login")
                || url.contains("/aclogin") || url.contains("/AcLogin");
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
     * label 텍스트로 textbox를 찾아 값을 입력한다.
     * 기존 값을 clear 한 후 fill 한다.
     */
    private void fillTextboxByLabel(Page page, String label, String value,
                                    PlaywrightManager pm) {
        if (value == null || value.isBlank()) return;

        try {
            Locator input = page.getByRole(
                    com.microsoft.playwright.options.AriaRole.TEXTBOX,
                    new Page.GetByRoleOptions().setName(label));

            if (input.count() > 0) {
                input.first().click();
                input.first().fill("");
                pm.randomDelay(100, 200);
                input.first().fill(value);
                pm.randomDelay(300, 500);
                return;
            }

            // 폴백: label 텍스트로 연결된 input 찾기 (JS)
            page.evaluate("(args) => {" +
                    "  const label = args[0];" +
                    "  const val = args[1];" +
                    "  const labels = document.querySelectorAll('label');" +
                    "  for (const lbl of labels) {" +
                    "    if (lbl.textContent.trim().includes(label)) {" +
                    "      const forId = lbl.getAttribute('for');" +
                    "      let input = forId ? document.getElementById(forId) : null;" +
                    "      if (!input) input = lbl.closest('.field, .form-group, div')?.querySelector('input, textarea');" +
                    "      if (input) {" +
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
            log.warn("[JobKoreaResume] '{}' 입력 실패: {}", label, e.getMessage());
        }
    }

    /**
     * placeholder 텍스트로 textbox를 찾아 값을 입력한다.
     */
    private void fillTextboxByPlaceholder(Page page, String placeholder, String value,
                                          PlaywrightManager pm) {
        if (value == null || value.isBlank()) return;

        try {
            Locator input = page.getByPlaceholder(placeholder);
            if (input.count() > 0) {
                input.first().click();
                input.first().fill("");
                pm.randomDelay(100, 200);
                input.first().fill(value);
                pm.randomDelay(300, 500);
                return;
            }

            // 부분 매칭 폴백
            Locator partialMatch = page.locator(
                    "input[placeholder*='" + placeholder.substring(0, Math.min(10, placeholder.length())) + "'], " +
                    "textarea[placeholder*='" + placeholder.substring(0, Math.min(10, placeholder.length())) + "']");
            if (partialMatch.count() > 0) {
                partialMatch.first().click();
                partialMatch.first().fill("");
                pm.randomDelay(100, 200);
                partialMatch.first().fill(value);
                pm.randomDelay(300, 500);
            }
        } catch (Exception e) {
            log.warn("[JobKoreaResume] placeholder '{}' 입력 실패: {}", placeholder, e.getMessage());
        }
    }

    /**
     * 토글 형태의 버튼(학교구분, 졸업상태, 총점 등)을 텍스트로 찾아 클릭한다.
     * 이미 선택된 상태면 스킵한다.
     */
    private void clickToggleButton(Page page, String buttonText, PlaywrightManager pm) {
        try {
            Locator btn = page.getByRole(
                    com.microsoft.playwright.options.AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName(buttonText).setExact(true));

            if (btn.count() > 0) {
                // 이미 활성화(aria-pressed, selected 등) 상태인지 확인
                String pressed = btn.first().getAttribute("aria-pressed");
                String className = btn.first().getAttribute("class");
                boolean alreadySelected = "true".equals(pressed)
                        || (className != null && (className.contains("active")
                        || className.contains("selected") || className.contains("on")));

                if (!alreadySelected) {
                    btn.first().click();
                    pm.randomDelay(300, 500);
                }
                return;
            }

            // 폴백: 텍스트 내용으로 버튼 찾기
            Locator fallbackBtn = page.locator(
                    "button:has-text('" + buttonText + "')");
            if (fallbackBtn.count() > 0) {
                fallbackBtn.first().click();
                pm.randomDelay(300, 500);
            }
        } catch (Exception e) {
            log.warn("[JobKoreaResume] 버튼 '{}' 클릭 실패: {}", buttonText, e.getMessage());
        }
    }

    /**
     * 텍스트로 버튼을 찾아 클릭한다 (일반 버튼, 추가 버튼 등).
     */
    private void clickButtonByText(Page page, String buttonText, PlaywrightManager pm) {
        try {
            Locator btn = page.getByRole(
                    com.microsoft.playwright.options.AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName(buttonText));

            if (btn.count() > 0) {
                btn.first().scrollIntoViewIfNeeded();
                pm.randomDelay(200, 400);
                btn.first().click();
                pm.shortDelay();
                return;
            }

            // 폴백: 텍스트 기반 locator
            Locator fallback = page.locator("button:has-text('" + buttonText + "')");
            if (fallback.count() > 0) {
                fallback.first().scrollIntoViewIfNeeded();
                pm.randomDelay(200, 400);
                fallback.first().click();
                pm.shortDelay();
            }
        } catch (Exception e) {
            log.warn("[JobKoreaResume] 버튼 '{}' 클릭 실패: {}", buttonText, e.getMessage());
        }
    }

    /**
     * 경력 섹션의 "추가" 버튼을 클릭한다.
     * 학력/경력/교육 등 여러 섹션에 "추가" 버튼이 있으므로 경력 영역에 한정한다.
     */
    private void clickCareerAddButton(Page page, PlaywrightManager pm) {
        try {
            // JS로 경력 섹션 내의 추가 버튼 클릭
            Object clicked = page.evaluate("() => {" +
                    "  const sections = document.querySelectorAll('section, [class*=career], [class*=경력]');" +
                    "  for (const section of sections) {" +
                    "    const heading = section.querySelector('h2, h3, .title, legend');" +
                    "    if (heading && heading.textContent.includes('경력')) {" +
                    "      const btn = section.querySelector('button');" +
                    "      if (btn && btn.textContent.includes('추가')) {" +
                    "        btn.scrollIntoView({behavior:'instant', block:'center'});" +
                    "        btn.click(); return true;" +
                    "      }" +
                    "    }" +
                    "  }" +
                    "  return false;" +
                    "}");

            if (!Boolean.TRUE.equals(clicked)) {
                // 폴백: ref 기반
                Locator addBtn = page.locator("[ref=e294]");
                if (addBtn.count() > 0) {
                    addBtn.first().click();
                }
            }

            pm.shortDelay();

        } catch (Exception e) {
            log.warn("[JobKoreaResume] 경력 추가 버튼 클릭 실패: {}", e.getMessage());
        }
    }

    /**
     * 체크박스를 label 텍스트로 찾아 체크한다.
     */
    private void checkCheckbox(Page page, String label, PlaywrightManager pm) {
        try {
            Locator checkbox = page.getByRole(
                    com.microsoft.playwright.options.AriaRole.CHECKBOX,
                    new Page.GetByRoleOptions().setName(label));

            if (checkbox.count() > 0) {
                if (!checkbox.first().isChecked()) {
                    checkbox.first().check();
                    pm.randomDelay(300, 500);
                }
                return;
            }

            // 폴백: JS
            page.evaluate("(label) => {" +
                    "  const labels = document.querySelectorAll('label');" +
                    "  for (const lbl of labels) {" +
                    "    if (lbl.textContent.trim().includes(label)) {" +
                    "      const cb = lbl.querySelector('input[type=checkbox]')" +
                    "        || document.getElementById(lbl.getAttribute('for'));" +
                    "      if (cb && !cb.checked) {" +
                    "        cb.click();" +
                    "        return;" +
                    "      }" +
                    "    }" +
                    "  }" +
                    "}", label);
            pm.randomDelay(300, 500);

        } catch (Exception e) {
            log.warn("[JobKoreaResume] 체크박스 '{}' 체크 실패: {}", label, e.getMessage());
        }
    }

    /**
     * "필드추가" 메뉴 버튼을 클릭하여 사이드 메뉴를 연다.
     */
    private void clickFieldAddMenuButton(Page page, PlaywrightManager pm) {
        try {
            Locator btn = page.getByRole(
                    com.microsoft.playwright.options.AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("필드추가"));

            if (btn.count() > 0) {
                btn.first().click();
                pm.shortDelay();
                return;
            }

            // 폴백: 텍스트 기반
            Locator fallback = page.locator("button:has-text('필드추가'), a:has-text('필드추가')");
            if (fallback.count() > 0) {
                fallback.first().click();
                pm.shortDelay();
            }
        } catch (Exception e) {
            log.warn("[JobKoreaResume] 필드추가 버튼 클릭 실패: {}", e.getMessage());
        }
    }

    /**
     * 사이드 메뉴에서 특정 항목(자격증, 자기소개서 등)을 클릭한다.
     */
    private void clickSideMenuItem(Page page, String menuText, PlaywrightManager pm) {
        try {
            // 링크 또는 버튼으로 찾기
            Locator menuItem = page.locator(
                    "a:has-text('" + menuText + "'), button:has-text('" + menuText + "'), " +
                    "li:has-text('" + menuText + "')");

            if (menuItem.count() > 0) {
                menuItem.first().click();
                pm.shortDelay();
                return;
            }

            // JS 폴백
            page.evaluate("(text) => {" +
                    "  const items = document.querySelectorAll('a, button, li');" +
                    "  for (const item of items) {" +
                    "    if (item.textContent.trim() === text" +
                    "      || item.textContent.trim().includes(text)) {" +
                    "      item.click(); return;" +
                    "    }" +
                    "  }" +
                    "}", menuText);
            pm.shortDelay();

        } catch (Exception e) {
            log.warn("[JobKoreaResume] 사이드 메뉴 '{}' 클릭 실패: {}", menuText, e.getMessage());
        }
    }

    /**
     * 필수 동의 체크박스를 체크한다.
     */
    private void agreeToTerms(Page page, PlaywrightManager pm) {
        try {
            // "필수동의 항목 및 개인정보 수집 및 이용 동의(선택)에 모두 동의합니다." 체크박스
            Locator agreeCheckbox = page.getByRole(
                    com.microsoft.playwright.options.AriaRole.CHECKBOX,
                    new Page.GetByRoleOptions().setName("필수동의"));

            if (agreeCheckbox.count() > 0) {
                if (!agreeCheckbox.first().isChecked()) {
                    agreeCheckbox.first().scrollIntoViewIfNeeded();
                    pm.randomDelay(300, 500);
                    agreeCheckbox.first().check();
                    pm.randomDelay(300, 500);
                }
                return;
            }

            // 폴백: 모두 동의 관련 체크박스 찾기
            page.evaluate("() => {" +
                    "  const labels = document.querySelectorAll('label');" +
                    "  for (const lbl of labels) {" +
                    "    if (lbl.textContent.includes('모두 동의') || lbl.textContent.includes('필수동의')) {" +
                    "      const cb = lbl.querySelector('input[type=checkbox]')" +
                    "        || document.getElementById(lbl.getAttribute('for'));" +
                    "      if (cb && !cb.checked) {" +
                    "        cb.click();" +
                    "        return;" +
                    "      }" +
                    "    }" +
                    "  }" +
                    "}");
            pm.randomDelay(300, 500);

        } catch (Exception e) {
            log.warn("[JobKoreaResume] 동의 체크박스 체크 실패: {}", e.getMessage());
        }
    }

    /**
     * DB 날짜 형식을 잡코리아 년월 형식으로 변환한다.
     * "2013-03-01" 또는 "201303" → "2013.03"
     */
    private String toJobKoreaYearMonth(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;

        String cleaned = dateStr.replace("-", "").replace(".", "").replace("/", "");
        if (cleaned.length() < 6) return null;

        String year = cleaned.substring(0, 4);
        String month = cleaned.substring(4, 6);
        return year + "." + month;
    }
}
