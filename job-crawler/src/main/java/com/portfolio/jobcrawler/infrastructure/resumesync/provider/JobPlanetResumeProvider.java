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
 * 잡플래닛 이력서 동기화 구현체.
 * 잡플래닛 이력서 편집 페이지(https://www.jobplanet.co.kr/profile/resumes/{id})의
 * 실제 HTML 구조에 맞춘 셀렉터를 사용한다.
 *
 * 핵심 규칙:
 * - Playwright의 getByRole/getByPlaceholder/locator 텍스트 기반 로케이터 사용
 * - 기존 데이터가 있으면 clear 후 입력
 * - 각 섹션별 SRP 준수 (메서드 분리)
 */
@Slf4j
@Component
public class JobPlanetResumeProvider implements ResumeProvider {

    private static final String SITE_NAME = "JOBPLANET";
    private static final String RESUME_LIST_URL =
            "https://www.jobplanet.co.kr/profile/resumes";

    private static final int TYPE_DELAY_MS = 80;

    /** 잡플래닛 학교구분 combobox 텍스트 매핑 */
    private static final Map<SchoolType, String> SCHOOL_TYPE_LABEL_MAP = Map.of(
            SchoolType.HIGH_SCHOOL, "고등학교 졸업",
            SchoolType.COLLEGE_2Y, "대학교졸업 (2,3년)",
            SchoolType.COLLEGE_4Y, "대학교졸업 (4년)",
            SchoolType.GRADUATE_MASTER, "대학원석사졸업",
            SchoolType.GRADUATE_DOCTOR, "대학원박사졸업"
    );

    @Override
    public String getSiteName() {
        return SITE_NAME;
    }

    @Override
    public ResumeSyncResult syncResume(Page page, PlaywrightManager pm, Resume resume) {
        log.info("[JobPlanetResume] 이력서 동기화 시작");

        try {
            // beforeunload 다이얼로그 자동 수락
            page.onDialog(dialog -> {
                log.info("[JobPlanetResume] 다이얼로그 자동 수락: {}", dialog.message());
                dialog.accept();
            });

            navigateToResumeEditPage(page, pm);

            String currentUrl = page.url();
            if (isLoginRedirect(currentUrl)) {
                log.error("[JobPlanetResume] 로그인 세션 만료: {}", currentUrl);
                return ResumeSyncResult.sessionExpired(
                        "잡플래닛 로그인 세션이 만료되었습니다. 설정에서 다시 연동해주세요.");
            }

            log.info("[JobPlanetResume] 이력서 편집 페이지 로드 완료: {}", currentUrl);

            // confirm/alert 자동 수락 오버라이드
            overrideDialogs(page);

            ResumeSyncResult.Builder result = ResumeSyncResult.builder();

            fillBasicInfo(page, pm, resume, result);
            fillEducations(page, pm, resume, result);
            fillCareers(page, pm, resume, result);
            fillSkills(page, pm, resume, result);
            fillActivities(page, pm, resume, result);
            fillCertifications(page, pm, resume, result);
            fillSelfIntroduction(page, pm, resume, result);
            saveResume(page, pm, result);

            return result.build();

        } catch (Exception e) {
            log.error("[JobPlanetResume] 동기화 실패: {}", e.getMessage(), e);
            return ResumeSyncResult.fail(e.getMessage());
        }
    }

    @Override
    public ResumeSyncResult updateSelfIntroduction(Page page, PlaywrightManager pm,
                                                   List<CoverLetterSection> sections) {
        log.info("[JobPlanetResume] 자기소개서 업데이트 시작 ({}개 문항)", sections.size());

        try {
            // beforeunload 다이얼로그 자동 수락
            page.onDialog(dialog -> {
                log.info("[JobPlanetResume] 다이얼로그 자동 수락: {}", dialog.message());
                dialog.accept();
            });

            navigateToResumeEditPage(page, pm);

            String currentUrl = page.url();
            if (isLoginRedirect(currentUrl)) {
                log.error("[JobPlanetResume] 로그인 세션 만료: {}", currentUrl);
                return ResumeSyncResult.sessionExpired(
                        "잡플래닛 로그인 세션이 만료되었습니다. 설정에서 다시 연동해주세요.");
            }

            log.info("[JobPlanetResume] 이력서 편집 페이지 로드 완료: {}", currentUrl);

            // confirm/alert 자동 수락 오버라이드
            overrideDialogs(page);

            // 자기소개 헤딩으로 스크롤
            scrollToHeading(page, "자기소개", pm);

            // 잡플래닛은 단일 textarea만 지원하므로 모든 섹션을 합쳐서 입력
            StringBuilder combined = new StringBuilder();
            for (int i = 0; i < sections.size(); i++) {
                CoverLetterSection section = sections.get(i);
                if (i > 0) {
                    combined.append("\n\n");
                }
                combined.append("[").append(section.title()).append("]\n");
                combined.append(section.content());
            }

            String combinedText = combined.toString();

            // 자기소개 textarea (placeholder에 "30자" 포함)
            Locator textarea = page.locator("textarea[placeholder*='30자']");
            if (textarea.count() == 0) {
                textarea = page.locator("textarea.medit");
            }
            if (textarea.count() == 0) {
                // 폴백: 자기소개 섹션 아래의 textarea
                textarea = page.locator("textarea").last();
            }

            if (textarea.count() > 0) {
                textarea.first().click();
                textarea.first().fill("");
                pm.randomDelay(100, 200);
                textarea.first().fill(combinedText);
                pm.randomDelay(300, 500);
            } else {
                // JS 폴백
                page.evaluate("(text) => {" +
                        "  const headings = document.querySelectorAll('h2, h3, h4');" +
                        "  for (const h of headings) {" +
                        "    if (h.textContent.includes('자기소개')) {" +
                        "      const section = h.closest('section') || h.parentElement;" +
                        "      const ta = section.querySelector('textarea');" +
                        "      if (ta) {" +
                        "        ta.value = text;" +
                        "        ta.dispatchEvent(new Event('input', {bubbles:true}));" +
                        "        ta.dispatchEvent(new Event('change', {bubbles:true}));" +
                        "        return;" +
                        "      }" +
                        "    }" +
                        "  }" +
                        "}", combinedText);
                pm.randomDelay(300, 500);
            }

            // 임시 저장 버튼 클릭
            Locator saveBtn = page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("임시 저장"));
            if (saveBtn.count() == 0) {
                saveBtn = page.locator("button:has-text('임시 저장'), button:has-text('저장')");
            }
            if (saveBtn.count() > 0) {
                saveBtn.first().scrollIntoViewIfNeeded();
                pm.randomDelay(500, 1000);
                saveBtn.first().click();
                pm.longDelay();
            }

            log.info("[JobPlanetResume] 자기소개서 업데이트 완료 ({}개 문항 합침)", sections.size());
            return ResumeSyncResult.success();

        } catch (Exception e) {
            log.error("[JobPlanetResume] 자기소개서 업데이트 실패: {}", e.getMessage(), e);
            return ResumeSyncResult.fail(e.getMessage());
        }
    }

    // ══════════════════════════════════════════════
    //  페이지 네비게이션
    // ══════════════════════════════════════════════

    /**
     * 이력서 목록 페이지로 이동한 후, 기존 이력서를 클릭하거나 신규 작성 페이지로 진입한다.
     */
    private void navigateToResumeEditPage(Page page, PlaywrightManager pm) {
        page.navigate(RESUME_LIST_URL,
                new Page.NavigateOptions().setWaitUntil(
                        com.microsoft.playwright.options.WaitUntilState.LOAD));
        pm.shortDelay();

        String currentUrl = page.url();
        if (isLoginRedirect(currentUrl)) {
            return; // 세션 만료는 호출부에서 처리
        }

        // 이력서 목록에서 첫 번째 이력서 클릭 시도 (이미 이력서가 있는 경우)
        Locator existingResume = page.locator("a[href*='/profile/resumes/']").first();
        if (existingResume.count() > 0) {
            existingResume.click();
            pm.shortDelay();
            log.info("[JobPlanetResume] 기존 이력서 편집 페이지로 이동: {}", page.url());
            return;
        }

        // 이력서가 없으면 신규 작성 버튼 클릭
        Locator newResumeBtn = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("이력서 작성"));
        if (newResumeBtn.count() > 0) {
            newResumeBtn.first().click();
            pm.shortDelay();
            log.info("[JobPlanetResume] 신규 이력서 작성 페이지로 이동: {}", page.url());
            return;
        }

        // 폴백: 이미 편집 페이지에 있을 수 있음
        log.info("[JobPlanetResume] 현재 페이지에서 이력서 편집 진행: {}", page.url());
    }

    // ══════════════════════════════════════════════
    //  기본 정보 (이름, 이메일, 전화번호, 성별, 생년)
    // ══════════════════════════════════════════════

    private void fillBasicInfo(Page page, PlaywrightManager pm, Resume resume,
                               ResumeSyncResult.Builder result) {
        try {
            // 이름 (pre-filled 되어 있을 수 있지만 갱신)
            fillTextboxByLabel(page, "이름", resume.getName(), pm);

            // 이메일
            fillTextboxByLabel(page, "이메일", resume.getEmail(), pm);

            // 전화번호 (잡플래닛: 대시 없이 "01089669730")
            String phone = removeDashes(resume.getPhone());
            fillTextboxByLabel(page, "전화번호", phone, pm);

            // 성별 select
            if (resume.getGender() != null && !resume.getGender().isBlank()) {
                selectOptionByLabel(page, "성별", resume.getGender(), pm);
            }

            // 생년 select (birthDate에서 년도 추출)
            if (resume.getBirthDate() != null && !resume.getBirthDate().isBlank()) {
                String birthYear = extractBirthYear(resume.getBirthDate());
                if (birthYear != null) {
                    selectOptionByLabel(page, "년", birthYear + "년", pm);
                }
            }

            result.addSectionSuccess("기본정보");
            log.info("[JobPlanetResume] 기본정보 입력 완료");

        } catch (Exception e) {
            log.error("[JobPlanetResume] 기본정보 입력 실패: {}", e.getMessage(), e);
            result.addSectionFail("기본정보", e.getMessage());
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

            // "학력" 헤딩으로 스크롤
            scrollToHeading(page, "학력", pm);

            for (int i = 0; i < educations.size(); i++) {
                ResumeEducation edu = educations.get(i);

                // 첫 번째 항목은 기본 폼이 열려있으므로 추가 버튼 불필요
                if (i > 0) {
                    clickAddButtonInSection(page, "학력", pm);
                }

                fillSingleEducation(page, pm, edu, i);
            }

            result.addSectionSuccess("학력");
            log.info("[JobPlanetResume] 학력 입력 완료 ({}건)", educations.size());

        } catch (Exception e) {
            log.error("[JobPlanetResume] 학력 입력 실패: {}", e.getMessage(), e);
            result.addSectionFail("학력", e.getMessage());
        }
    }

    private void fillSingleEducation(Page page, PlaywrightManager pm,
                                     ResumeEducation edu, int index) {
        // 학교구분 combobox 선택
        if (edu.getSchoolType() != null) {
            String label = SCHOOL_TYPE_LABEL_MAP.getOrDefault(
                    edu.getSchoolType(), "대학교졸업 (4년)");
            selectComboboxInSection(page, "학교 구분", label, pm);
        }

        // 학교명
        fillTextboxByPlaceholder(page, "학교명", edu.getSchoolName(), pm);

        // 전공 및 학위
        fillTextboxByPlaceholder(page, "전공 및 학위", edu.getMajor(), pm);

        // 입학 년/월
        String startDate = toJobPlanetYearMonth(edu.getStartDate());
        if (startDate != null) {
            fillDateFieldInSection(page, "학력", startDate, true, index, pm);
        }

        // 졸업 년/월
        String endDate = toJobPlanetYearMonth(edu.getEndDate());
        if (endDate != null) {
            fillDateFieldInSection(page, "학력", endDate, false, index, pm);
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
                // 신입 체크박스 체크
                checkCheckbox(page, "신입입니다", pm);
                result.addSectionResult("경력", true, "스킵 (신입)");
                return;
            }

            // "경력" 헤딩으로 스크롤
            scrollToHeading(page, "경력", pm);

            for (int i = 0; i < careers.size(); i++) {
                ResumeCareer career = careers.get(i);

                if (i > 0) {
                    clickAddButtonInSection(page, "경력", pm);
                }

                fillSingleCareer(page, pm, career, i);
            }

            result.addSectionSuccess("경력");
            log.info("[JobPlanetResume] 경력 입력 완료 ({}건)", careers.size());

        } catch (Exception e) {
            log.error("[JobPlanetResume] 경력 입력 실패: {}", e.getMessage(), e);
            result.addSectionFail("경력", e.getMessage());
        }
    }

    private void fillSingleCareer(Page page, PlaywrightManager pm,
                                  ResumeCareer career, int index) {
        // 기업명
        fillTextboxByPlaceholder(page, "기업명", career.getCompanyName(), pm);

        // 부서
        if (career.getDepartment() != null && !career.getDepartment().isBlank()) {
            fillTextboxByPlaceholder(page, "부서", career.getDepartment(), pm);
        }

        // 직책
        if (career.getRank() != null && !career.getRank().isBlank()) {
            fillTextboxByPlaceholder(page, "직책", career.getRank(), pm);
        }

        // 직무
        if (career.getPosition() != null && !career.getPosition().isBlank()) {
            fillTextboxByPlaceholder(page, "직무", career.getPosition(), pm);
        }

        // 기본 연봉 정보
        if (career.getSalary() != null && !career.getSalary().isBlank()) {
            fillTextboxByLabel(page, "기본 연봉 정보", career.getSalary(), pm);
        }

        // 입사 년/월
        String startDate = toJobPlanetYearMonth(career.getStartDate());
        if (startDate != null) {
            fillDateFieldInSection(page, "경력", startDate, true, index, pm);
        }

        // 재직중 체크박스 또는 퇴사 년/월
        if (career.isCurrentlyWorking()) {
            checkCheckbox(page, "재직중", pm);
        } else {
            String endDate = toJobPlanetYearMonth(career.getEndDate());
            if (endDate != null) {
                fillDateFieldInSection(page, "경력", endDate, false, index, pm);
            }
        }

        // 업무 내용 (큰 textarea)
        if (career.getJobDescription() != null && !career.getJobDescription().isBlank()) {
            fillJobDescriptionTextarea(page, career.getJobDescription(), pm);
        }
    }

    // ══════════════════════════════════════════════
    //  스킬
    // ══════════════════════════════════════════════

    private void fillSkills(Page page, PlaywrightManager pm, Resume resume,
                            ResumeSyncResult.Builder result) {
        try {
            List<ResumeSkill> skills = resume.getSkills();
            if (skills == null || skills.isEmpty()) {
                result.addSectionResult("스킬", true, "스킵 (데이터 없음)");
                return;
            }

            scrollToHeading(page, "스킬", pm);

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
            log.info("[JobPlanetResume] 스킬 입력 완료 ({}/{}건)", addedCount, skills.size());

        } catch (Exception e) {
            log.error("[JobPlanetResume] 스킬 입력 실패: {}", e.getMessage(), e);
            result.addSectionFail("스킬", e.getMessage());
        }
    }

    /**
     * 스킬 입력창에 스킬명을 타이핑하고 Enter로 추가한다.
     * 잡플래닛은 스킬을 직접 타이핑 후 추가하는 방식이다.
     */
    private boolean addSingleSkill(Page page, PlaywrightManager pm, String skillName) {
        try {
            Locator searchInput = page.getByPlaceholder("스킬을 입력해 주세요");
            if (searchInput.count() == 0) {
                // 폴백: 부분 매칭
                searchInput = page.locator("input[placeholder*='스킬']");
            }

            if (searchInput.count() == 0) {
                log.warn("[JobPlanetResume] 스킬 입력창을 찾을 수 없음");
                return false;
            }

            searchInput.first().click();
            searchInput.first().fill("");
            pm.randomDelay(100, 200);
            searchInput.first().type(skillName,
                    new Locator.TypeOptions().setDelay(TYPE_DELAY_MS));
            pm.randomDelay(500, 800);

            // 자동완성 목록에서 매칭되는 항목 클릭 시도
            Locator suggestion = page.locator("li, [role='option']")
                    .filter(new Locator.FilterOptions().setHasText(skillName));
            if (suggestion.count() > 0) {
                suggestion.first().click();
                pm.randomDelay(300, 500);
                log.info("[JobPlanetResume] 스킬 자동완성 선택: {}", skillName);
                return true;
            }

            // 자동완성 없으면 Enter로 직접 추가
            searchInput.first().press("Enter");
            pm.randomDelay(300, 500);
            log.info("[JobPlanetResume] 스킬 직접 추가: {}", skillName);
            return true;

        } catch (Exception e) {
            log.warn("[JobPlanetResume] 스킬 추가 실패 ({}): {}", skillName, e.getMessage());
            return false;
        }
    }

    // ══════════════════════════════════════════════
    //  활동 및 수상
    // ══════════════════════════════════════════════

    private void fillActivities(Page page, PlaywrightManager pm, Resume resume,
                                ResumeSyncResult.Builder result) {
        try {
            List<ResumeActivity> activities = resume.getActivities();
            if (activities == null || activities.isEmpty()) {
                result.addSectionResult("활동 및 수상", true, "스킵 (데이터 없음)");
                return;
            }

            scrollToHeading(page, "활동 및 수상", pm);

            for (int i = 0; i < activities.size(); i++) {
                ResumeActivity activity = activities.get(i);

                if (i > 0) {
                    clickAddButtonInSection(page, "활동 및 수상", pm);
                }

                fillSingleActivity(page, pm, activity, i);
            }

            result.addSectionSuccess("활동 및 수상");
            log.info("[JobPlanetResume] 활동 및 수상 입력 완료 ({}건)", activities.size());

        } catch (Exception e) {
            log.error("[JobPlanetResume] 활동 및 수상 입력 실패: {}", e.getMessage(), e);
            result.addSectionFail("활동 및 수상", e.getMessage());
        }
    }

    private void fillSingleActivity(Page page, PlaywrightManager pm,
                                    ResumeActivity activity, int index) {
        // 활동 및 수상명
        fillTextboxByPlaceholder(page, "활동 및 수상명", activity.getActivityName(), pm);

        // 활동 내용 설명
        if (activity.getDescription() != null && !activity.getDescription().isBlank()) {
            fillActivityDescription(page, activity.getDescription(), pm);
        }

        // 시작 년/월
        String startDate = toJobPlanetYearMonth(activity.getStartDate());
        if (startDate != null) {
            fillDateFieldInSection(page, "활동 및 수상", startDate, true, index, pm);
        }

        // 종료 년/월
        String endDate = toJobPlanetYearMonth(activity.getEndDate());
        if (endDate != null) {
            fillDateFieldInSection(page, "활동 및 수상", endDate, false, index, pm);
        }
    }

    // ══════════════════════════════════════════════
    //  자격증 및 기타
    // ══════════════════════════════════════════════

    private void fillCertifications(Page page, PlaywrightManager pm, Resume resume,
                                    ResumeSyncResult.Builder result) {
        try {
            List<ResumeCertification> certifications = resume.getCertifications();
            if (certifications == null || certifications.isEmpty()) {
                result.addSectionResult("자격증", true, "스킵 (데이터 없음)");
                return;
            }

            scrollToHeading(page, "자격증 및 기타", pm);

            for (int i = 0; i < certifications.size(); i++) {
                ResumeCertification cert = certifications.get(i);

                if (i > 0) {
                    clickAddButtonInSection(page, "자격증 및 기타", pm);
                }

                fillSingleCertification(page, pm, cert, i);
            }

            result.addSectionSuccess("자격증");
            log.info("[JobPlanetResume] 자격증 입력 완료 ({}건)", certifications.size());

        } catch (Exception e) {
            log.error("[JobPlanetResume] 자격증 입력 실패: {}", e.getMessage(), e);
            result.addSectionFail("자격증", e.getMessage());
        }
    }

    private void fillSingleCertification(Page page, PlaywrightManager pm,
                                         ResumeCertification cert, int index) {
        // 취득 년/월
        String acquiredDate = toJobPlanetYearMonth(cert.getAcquiredDate());
        if (acquiredDate != null) {
            fillDateFieldInSection(page, "자격증 및 기타", acquiredDate, true, index, pm);
        }

        // 자격증명 - JS로 자격증 섹션 내의 텍스트 입력란에 입력
        if (cert.getCertName() != null && !cert.getCertName().isBlank()) {
            fillCertNameInSection(page, cert.getCertName(), pm);
        }
    }

    // ══════════════════════════════════════════════
    //  자기소개
    // ══════════════════════════════════════════════

    private void fillSelfIntroduction(Page page, PlaywrightManager pm, Resume resume,
                                      ResumeSyncResult.Builder result) {
        try {
            String selfIntro = resume.getSelfIntroduction();
            if (selfIntro == null || selfIntro.isBlank()) {
                result.addSectionResult("자기소개", true, "스킵 (데이터 없음)");
                return;
            }

            scrollToHeading(page, "자기소개", pm);

            // 자기소개 영역의 큰 textarea (placeholder에 "30자" 포함)
            Locator textarea = page.locator("textarea[placeholder*='30자']");
            if (textarea.count() == 0) {
                // 폴백: 자기소개 섹션 아래의 textarea
                textarea = page.locator("textarea").last();
            }

            if (textarea.count() > 0) {
                textarea.first().click();
                textarea.first().fill("");
                pm.randomDelay(100, 200);
                textarea.first().fill(selfIntro);
                pm.randomDelay(300, 500);
            } else {
                // JS 폴백
                page.evaluate("(text) => {" +
                        "  const headings = document.querySelectorAll('h2, h3, h4');" +
                        "  for (const h of headings) {" +
                        "    if (h.textContent.includes('자기소개')) {" +
                        "      const section = h.closest('section') || h.parentElement;" +
                        "      const ta = section.querySelector('textarea');" +
                        "      if (ta) {" +
                        "        ta.value = text;" +
                        "        ta.dispatchEvent(new Event('input', {bubbles:true}));" +
                        "        ta.dispatchEvent(new Event('change', {bubbles:true}));" +
                        "        return;" +
                        "      }" +
                        "    }" +
                        "  }" +
                        "}", selfIntro);
                pm.randomDelay(300, 500);
            }

            result.addSectionSuccess("자기소개");
            log.info("[JobPlanetResume] 자기소개 입력 완료");

        } catch (Exception e) {
            log.error("[JobPlanetResume] 자기소개 입력 실패: {}", e.getMessage(), e);
            result.addSectionFail("자기소개", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════
    //  최종 저장
    // ══════════════════════════════════════════════

    private void saveResume(Page page, PlaywrightManager pm, ResumeSyncResult.Builder result) {
        try {
            // "임시 저장" 버튼 클릭
            Locator saveBtn = page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("임시 저장"));

            if (saveBtn.count() == 0) {
                // 폴백: 텍스트로 찾기
                saveBtn = page.locator("button:has-text('임시 저장'), button:has-text('저장')");
            }

            if (saveBtn.count() == 0) {
                log.error("[JobPlanetResume] 저장 버튼을 찾을 수 없음");
                result.addSectionFail("최종저장", "저장 버튼 없음");
                return;
            }

            saveBtn.first().scrollIntoViewIfNeeded();
            pm.randomDelay(500, 1000);
            saveBtn.first().click();
            log.info("[JobPlanetResume] 임시 저장 버튼 클릭");

            pm.longDelay();

            // 저장 결과 확인 - 세션 만료 재확인
            String finalUrl = page.url();
            if (isLoginRedirect(finalUrl)) {
                result.addSectionFail("최종저장", "저장 중 세션 만료");
                return;
            }

            // 에러 메시지 확인
            Locator errorMsg = page.locator(
                    ".error, .alert-danger, [class*=error], [class*=alert]");
            if (errorMsg.count() > 0 && errorMsg.first().isVisible()) {
                String errText = errorMsg.first().textContent();
                log.error("[JobPlanetResume] 저장 실패 - 에러: {}", errText);
                result.addSectionFail("최종저장", "잡플래닛 에러: " + errText);
            } else {
                result.addSectionSuccess("최종저장");
                log.info("[JobPlanetResume] 이력서 저장 성공: {}", finalUrl);
            }

        } catch (Exception e) {
            log.error("[JobPlanetResume] 최종 저장 실패: {}", e.getMessage(), e);
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
        return url.contains("/sign_in") || url.contains("/login");
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
            log.warn("[JobPlanetResume] '{}' 입력 실패: {}", label, e.getMessage());
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
            String partial = placeholder.substring(0, Math.min(8, placeholder.length()));
            Locator partialMatch = page.locator(
                    "input[placeholder*='" + partial + "'], " +
                    "textarea[placeholder*='" + partial + "']");
            if (partialMatch.count() > 0) {
                partialMatch.first().click();
                partialMatch.first().fill("");
                pm.randomDelay(100, 200);
                partialMatch.first().fill(value);
                pm.randomDelay(300, 500);
            }
        } catch (Exception e) {
            log.warn("[JobPlanetResume] placeholder '{}' 입력 실패: {}", placeholder, e.getMessage());
        }
    }

    /**
     * select/combobox에서 옵션을 선택한다. label로 select를 찾고 옵션 텍스트로 선택한다.
     */
    private void selectOptionByLabel(Page page, String label, String optionText,
                                     PlaywrightManager pm) {
        try {
            // getByRole combobox
            Locator combobox = page.getByRole(AriaRole.COMBOBOX,
                    new Page.GetByRoleOptions().setName(label));
            if (combobox.count() > 0) {
                combobox.first().selectOption(optionText);
                pm.randomDelay(300, 500);
                return;
            }

            // 폴백: JS로 select 요소 찾기
            page.evaluate("(args) => {" +
                    "  const label = args[0];" +
                    "  const optText = args[1];" +
                    "  const labels = document.querySelectorAll('label, .label');" +
                    "  for (const lbl of labels) {" +
                    "    if (lbl.textContent.includes(label)) {" +
                    "      const select = lbl.closest('div')?.querySelector('select');" +
                    "      if (select) {" +
                    "        for (const opt of select.options) {" +
                    "          if (opt.textContent.includes(optText)) {" +
                    "            select.value = opt.value;" +
                    "            select.dispatchEvent(new Event('change', {bubbles:true}));" +
                    "            return;" +
                    "          }" +
                    "        }" +
                    "      }" +
                    "    }" +
                    "  }" +
                    "}", new Object[]{label, optionText});
            pm.randomDelay(300, 500);

        } catch (Exception e) {
            log.warn("[JobPlanetResume] select '{}' 선택 실패: {}", label, e.getMessage());
        }
    }

    /**
     * 섹션 내 combobox에서 옵션을 선택한다 (학교 구분 등).
     */
    private void selectComboboxInSection(Page page, String comboboxName, String optionText,
                                         PlaywrightManager pm) {
        try {
            Locator combobox = page.getByRole(AriaRole.COMBOBOX,
                    new Page.GetByRoleOptions().setName(comboboxName));
            if (combobox.count() > 0) {
                combobox.first().click();
                pm.randomDelay(200, 400);

                // 옵션 선택
                Locator option = page.getByRole(AriaRole.OPTION,
                        new Page.GetByRoleOptions().setName(optionText));
                if (option.count() > 0) {
                    option.first().click();
                } else {
                    // selectOption 폴백
                    combobox.first().selectOption(optionText);
                }
                pm.randomDelay(300, 500);
                return;
            }

            // 폴백: select 태그로 직접 선택
            selectOptionByLabel(page, comboboxName, optionText, pm);

        } catch (Exception e) {
            log.warn("[JobPlanetResume] combobox '{}' 선택 실패: {}", comboboxName, e.getMessage());
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

            // 폴백: label 텍스트로 체크박스 찾기 (JS)
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
            log.warn("[JobPlanetResume] 체크박스 '{}' 체크 실패: {}", label, e.getMessage());
        }
    }

    /**
     * 특정 헤딩 텍스트가 있는 영역으로 스크롤한다.
     */
    private void scrollToHeading(Page page, String headingText, PlaywrightManager pm) {
        try {
            Locator heading = page.getByRole(AriaRole.HEADING,
                    new Page.GetByRoleOptions().setName(headingText));
            if (heading.count() > 0) {
                heading.first().scrollIntoViewIfNeeded();
                pm.randomDelay(300, 500);
                return;
            }

            // 폴백: 텍스트 기반
            page.evaluate("(text) => {" +
                    "  const headings = document.querySelectorAll('h1, h2, h3, h4');" +
                    "  for (const h of headings) {" +
                    "    if (h.textContent.includes(text)) {" +
                    "      h.scrollIntoView({behavior:'instant', block:'center'});" +
                    "      return;" +
                    "    }" +
                    "  }" +
                    "}", headingText);
            pm.randomDelay(300, 500);

        } catch (Exception e) {
            log.warn("[JobPlanetResume] 헤딩 '{}' 스크롤 실패: {}", headingText, e.getMessage());
        }
    }

    /**
     * 섹션 내의 "+ 추가" 버튼을 클릭한다.
     * 섹션 헤딩 텍스트를 기준으로 해당 섹션의 추가 버튼을 식별한다.
     */
    private void clickAddButtonInSection(Page page, String sectionHeading, PlaywrightManager pm) {
        try {
            // JS로 섹션 내 "+ 추가" 버튼 클릭
            Boolean clicked = (Boolean) page.evaluate("(heading) => {" +
                    "  const headings = document.querySelectorAll('h1, h2, h3, h4');" +
                    "  for (const h of headings) {" +
                    "    if (h.textContent.includes(heading)) {" +
                    "      const section = h.closest('section') || h.closest('[class*=section]')" +
                    "        || h.parentElement?.parentElement;" +
                    "      if (section) {" +
                    "        const btns = section.querySelectorAll('button');" +
                    "        for (const btn of btns) {" +
                    "          if (btn.textContent.includes('추가')) {" +
                    "            btn.scrollIntoView({behavior:'instant', block:'center'});" +
                    "            btn.click();" +
                    "            return true;" +
                    "          }" +
                    "        }" +
                    "      }" +
                    "    }" +
                    "  }" +
                    "  return false;" +
                    "}", sectionHeading);

            if (!Boolean.TRUE.equals(clicked)) {
                // 폴백: 일반 "+ 추가" 버튼
                Locator addBtn = page.locator("button:has-text('추가')");
                if (addBtn.count() > 0) {
                    addBtn.last().scrollIntoViewIfNeeded();
                    pm.randomDelay(200, 400);
                    addBtn.last().click();
                }
            }

            pm.shortDelay();

        } catch (Exception e) {
            log.warn("[JobPlanetResume] '{}' 섹션 추가 버튼 클릭 실패: {}", sectionHeading, e.getMessage());
        }
    }

    /**
     * 섹션 내 "년/월" 형식 날짜 입력란에 값을 입력한다.
     * 잡플래닛은 "년/월" placeholder의 textbox를 사용하며, 각 섹션에 시작/종료 2개가 있다.
     *
     * @param isStart true이면 첫 번째(시작일), false이면 두 번째(종료일)
     * @param entryIndex 해당 섹션 내 몇 번째 항목인지 (0-based)
     */
    private void fillDateFieldInSection(Page page, String sectionHeading, String dateValue,
                                        boolean isStart, int entryIndex, PlaywrightManager pm) {
        if (dateValue == null || dateValue.isBlank()) return;

        try {
            Locator dateInputs = page.getByPlaceholder("년/월");
            if (dateInputs.count() == 0) {
                return;
            }

            // 섹션 내의 날짜 필드를 JS로 찾아서 입력
            page.evaluate("(args) => {" +
                    "  const heading = args[0];" +
                    "  const value = args[1];" +
                    "  const isStart = args[2];" +
                    "  const entryIdx = args[3];" +
                    "  const headings = document.querySelectorAll('h1, h2, h3, h4');" +
                    "  for (const h of headings) {" +
                    "    if (h.textContent.includes(heading)) {" +
                    "      const section = h.closest('section') || h.closest('[class*=section]')" +
                    "        || h.parentElement?.parentElement;" +
                    "      if (section) {" +
                    "        const inputs = section.querySelectorAll('input[placeholder*=\"년/월\"]');" +
                    "        const idx = entryIdx * 2 + (isStart ? 0 : 1);" +
                    "        if (inputs[idx]) {" +
                    "          inputs[idx].value = value;" +
                    "          inputs[idx].dispatchEvent(new Event('input', {bubbles:true}));" +
                    "          inputs[idx].dispatchEvent(new Event('change', {bubbles:true}));" +
                    "        }" +
                    "      }" +
                    "      return;" +
                    "    }" +
                    "  }" +
                    "}", new Object[]{sectionHeading, dateValue, isStart, entryIndex});
            pm.randomDelay(200, 400);

        } catch (Exception e) {
            log.warn("[JobPlanetResume] '{}' 섹션 날짜 입력 실패: {}", sectionHeading, e.getMessage());
        }
    }

    /**
     * 경력 섹션의 업무 내용 textarea에 값을 입력한다.
     * 잡플래닛은 업무 내용에 큰 textarea를 사용한다.
     */
    private void fillJobDescriptionTextarea(Page page, String description, PlaywrightManager pm) {
        try {
            Locator textarea = page.locator("textarea[placeholder*='업무']");
            if (textarea.count() == 0) {
                // 폴백: 경력 섹션 내의 textarea
                textarea = page.locator("textarea[placeholder*='내용']");
            }

            if (textarea.count() > 0) {
                textarea.last().click();
                textarea.last().fill("");
                pm.randomDelay(100, 200);
                textarea.last().fill(description);
                pm.randomDelay(300, 500);
            }
        } catch (Exception e) {
            log.warn("[JobPlanetResume] 업무 내용 입력 실패: {}", e.getMessage());
        }
    }

    /**
     * 활동 및 수상 섹션의 설명 textarea에 값을 입력한다.
     */
    private void fillActivityDescription(Page page, String description, PlaywrightManager pm) {
        try {
            // 활동 및 수상 섹션 내 textarea (JS로 섹션 특정)
            page.evaluate("(text) => {" +
                    "  const headings = document.querySelectorAll('h1, h2, h3, h4');" +
                    "  for (const h of headings) {" +
                    "    if (h.textContent.includes('활동 및 수상')) {" +
                    "      const section = h.closest('section') || h.closest('[class*=section]')" +
                    "        || h.parentElement?.parentElement;" +
                    "      if (section) {" +
                    "        const ta = section.querySelector('textarea');" +
                    "        if (ta) {" +
                    "          ta.value = text;" +
                    "          ta.dispatchEvent(new Event('input', {bubbles:true}));" +
                    "          ta.dispatchEvent(new Event('change', {bubbles:true}));" +
                    "        }" +
                    "      }" +
                    "      return;" +
                    "    }" +
                    "  }" +
                    "}", description);
            pm.randomDelay(300, 500);

        } catch (Exception e) {
            log.warn("[JobPlanetResume] 활동 설명 입력 실패: {}", e.getMessage());
        }
    }

    /**
     * 자격증 섹션 내에서 자격증명을 입력한다.
     */
    private void fillCertNameInSection(Page page, String certName, PlaywrightManager pm) {
        try {
            page.evaluate("(name) => {" +
                    "  const headings = document.querySelectorAll('h1, h2, h3, h4');" +
                    "  for (const h of headings) {" +
                    "    if (h.textContent.includes('자격증')) {" +
                    "      const section = h.closest('section') || h.closest('[class*=section]')" +
                    "        || h.parentElement?.parentElement;" +
                    "      if (section) {" +
                    "        const inputs = section.querySelectorAll('input[type=text], input:not([type])');" +
                    "        for (const input of inputs) {" +
                    "          if (!input.placeholder?.includes('년/월')) {" +
                    "            input.value = name;" +
                    "            input.dispatchEvent(new Event('input', {bubbles:true}));" +
                    "            input.dispatchEvent(new Event('change', {bubbles:true}));" +
                    "            return;" +
                    "          }" +
                    "        }" +
                    "      }" +
                    "      return;" +
                    "    }" +
                    "  }" +
                    "}", certName);
            pm.randomDelay(300, 500);

        } catch (Exception e) {
            log.warn("[JobPlanetResume] 자격증명 입력 실패: {}", e.getMessage());
        }
    }

    /**
     * DB 날짜 형식을 잡플래닛 년월 형식으로 변환한다.
     * "2013-03-01" 또는 "201303" -> "2017.07"
     */
    private String toJobPlanetYearMonth(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;

        String cleaned = dateStr.replace("-", "").replace(".", "").replace("/", "");
        if (cleaned.length() < 6) return null;

        String year = cleaned.substring(0, 4);
        String month = cleaned.substring(4, 6);
        return year + "." + month;
    }

    /**
     * 전화번호에서 대시를 제거한다.
     * "010-8966-9730" -> "01089669730"
     */
    private String removeDashes(String phone) {
        if (phone == null || phone.isBlank()) return phone;
        return phone.replace("-", "").replace(" ", "");
    }

    /**
     * 생년월일에서 년도를 추출한다.
     * "1994-05-20" 또는 "19940520" -> "1994"
     */
    private String extractBirthYear(String birthDate) {
        if (birthDate == null || birthDate.isBlank()) return null;

        String cleaned = birthDate.replace("-", "").replace(".", "").replace("/", "");
        if (cleaned.length() < 4) return null;

        return cleaned.substring(0, 4);
    }
}
