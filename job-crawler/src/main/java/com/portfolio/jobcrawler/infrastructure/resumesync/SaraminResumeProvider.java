package com.portfolio.jobcrawler.infrastructure.resumesync;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.portfolio.jobcrawler.domain.resume.entity.*;
import com.portfolio.jobcrawler.domain.resume.vo.ActivityType;
import com.portfolio.jobcrawler.domain.resume.vo.GraduationStatus;
import com.portfolio.jobcrawler.domain.resume.vo.SchoolType;
import com.portfolio.jobcrawler.infrastructure.crawler.PlaywrightManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 사람인 이력서 동기화 구현체.
 * 실제 사람인 HTML 구조에 맞춘 셀렉터를 사용한다.
 *
 * 핵심 규칙:
 * - 모든 버튼 클릭은 page.evaluate() JS 방식 (jQuery 이벤트 핸들러 트리거 보장)
 * - 모든 select 변경은 page.evaluate()로 value 세팅 + change 이벤트 dispatch
 * - 자동완성 input은 반드시 type() (천천히 100ms delay) - fill() 하면 이벤트 안 먹힘
 * - 열린 폼 찾기: section#xxx .resume_edit:not([style*='display:none']) 의 마지막 것
 */
@Slf4j
@Component
public class SaraminResumeProvider implements ResumeProvider {

    private static final String RESUME_WRITE_URL =
            "https://www.saramin.co.kr/zf_user/member/resume-manage/write?template_cd=1";

    private static final int AUTOCOMPLETE_WAIT_MS = 3000;
    private static final int TYPE_DELAY_MS = 100;

    private static final Map<SchoolType, String> SCHOOL_GB_MAP = Map.of(
            SchoolType.COLLEGE_2Y, "college",
            SchoolType.COLLEGE_4Y, "university",
            SchoolType.GRADUATE_MASTER, "master",
            SchoolType.GRADUATE_DOCTOR, "doctor"
    );

    private static final Map<GraduationStatus, String> GRADUATION_GB_MAP = Map.of(
            GraduationStatus.GRADUATED, "1",
            GraduationStatus.ENROLLED, "2",
            GraduationStatus.LEAVE_OF_ABSENCE, "3",
            GraduationStatus.COMPLETED, "4",
            GraduationStatus.DROPPED, "5",
            GraduationStatus.EXPECTED, "6"
    );

    private static final Map<String, String> LANGUAGE_CODE_MAP = Map.of(
            "영어", "1", "일본어", "2", "중국어", "3", "독일어", "4",
            "불어", "5", "스페인어", "6", "러시아어", "7", "이탈리아어", "8",
            "한국어", "45"
    );

    private static final Map<ActivityType, String> ACTIVITY_CD_MAP = Map.of(
            ActivityType.INTERN, "2",
            ActivityType.EXTERNAL_ACTIVITY, "6",
            ActivityType.EDUCATION, "education",
            ActivityType.AWARD, "7",
            ActivityType.OVERSEAS, "abroad"
    );

    @Override
    public String getSiteName() {
        return "SARAMIN";
    }

    @Override
    public ResumeSyncResult syncResume(Page page, PlaywrightManager pm, Resume resume) {
        log.info("[SaraminResume] 이력서 동기화 시작");

        ResumeSyncResult.Builder result = ResumeSyncResult.builder();

        // 1. 이력서 작성 페이지 이동
        try {
            // beforeunload 다이얼로그 자동 수락
            page.onDialog(dialog -> {
                log.info("[SaraminResume] 다이얼로그 자동 수락: {}", dialog.message());
                dialog.accept();
            });

            page.navigate(RESUME_WRITE_URL,
                    new Page.NavigateOptions()
                            .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.NETWORKIDLE)
                            .setTimeout(60000));
            pm.shortDelay();

            String currentUrl = page.url();
            log.info("[SaraminResume] 최종 URL: {}", currentUrl);

            if (currentUrl.contains("/login") || currentUrl.contains("/auth")) {
                log.error("[SaraminResume] 로그인 세션 만료: {}", currentUrl);
                return ResumeSyncResult.sessionExpired("사람인 로그인 세션이 만료되었습니다. 설정에서 다시 연동해주세요.");
            }

            try {
                page.waitForSelector("section#school",
                        new Page.WaitForSelectorOptions().setTimeout(30000));
            } catch (Exception waitEx) {
                String bodySnippet = "";
                try {
                    bodySnippet = (String) page.evaluate(
                            "() => document.body ? document.body.innerText.substring(0, 500) : 'no body'");
                } catch (Exception ignored) {}
                log.error("[SaraminResume] section#school 대기 타임아웃. URL: {}, body: {}", currentUrl, bodySnippet);
                return ResumeSyncResult.fail("이력서 작성 페이지 로딩 실패. URL: " + currentUrl);
            }

            log.info("[SaraminResume] 이력서 작성 페이지 로드 완료");
            dismissOverlays(page, pm);

        } catch (Exception e) {
            log.error("[SaraminResume] 페이지 이동 실패: {}", e.getMessage());
            return ResumeSyncResult.sessionExpired("사람인 접속 실패. 세션이 만료되었을 수 있습니다.");
        }

        // 2. cleanup: 열린 폼 취소 + invalid 항목 삭제 + confirm 오버라이드
        cleanupExistingItems(page, pm);

        // 3. 각 섹션 입력 (사람인 좌측 메뉴 순서)
        fillEducations(page, pm, resume, result);
        fillCareers(page, pm, resume, result);
        fillSkills(page, pm, resume, result);
        fillActivities(page, pm, resume, result);
        fillCertifications(page, pm, resume, result);
        fillLanguages(page, pm, resume, result);
        fillSelfIntroduction(page, pm, resume, result);
        saveResume(page, pm, result);

        return result.build();
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

            for (ResumeEducation edu : educations) {
                // 추가 버튼 클릭 (JS)
                if (!clickAddButton(page, "section#school", pm)) {
                    log.warn("[SaraminResume] 학력 추가 버튼을 찾을 수 없음");
                    result.addSectionFail("학력", "추가 버튼 없음");
                    return;
                }

                // 학력구분 select → "university" (대학ㆍ대학원 이상)
                selectOptionByJs(page, "section#school", "select[name='school_type']", "university");
                pm.randomDelay(500, 1000);

                // 대학구분 select → college/university/master/doctor
                if (edu.getSchoolType() != null) {
                    String gbValue = SCHOOL_GB_MAP.getOrDefault(edu.getSchoolType(), "university");
                    selectOptionByJs(page, "section#school", "select[name='school_gb[]']", gbValue);
                    pm.randomDelay(500, 1000);
                }

                // 학교명 (자동완성: type → 3초 대기 → li.auto_list a 클릭)
                if (edu.getSchoolName() != null) {
                    fillSchoolAutocomplete(page, edu.getSchoolName(), pm);
                }

                // 전공
                if (edu.getMajor() != null) {
                    fillInputInOpenForm(page, "section#school", "input[name='school_major[]']",
                            edu.getMajor());
                    pm.randomDelay(300, 500);
                }

                // 졸업여부 select
                if (edu.getGraduationStatus() != null) {
                    String gradValue = GRADUATION_GB_MAP.getOrDefault(
                            edu.getGraduationStatus(), "1");
                    selectOptionByJs(page, "section#school",
                            "select[name='school_graduation_gb[]']", gradValue);
                    pm.randomDelay(300, 500);
                }

                // 입학년월 (YYYYMM)
                String startDate = toSaraminDate(edu.getStartDate());
                if (startDate != null) {
                    fillInputInOpenForm(page, "section#school",
                            "input[name='school_entrance_dt[]']", startDate);
                    pm.randomDelay(300, 500);
                }

                // 졸업년월 (YYYYMM)
                String endDate = toSaraminDate(edu.getEndDate());
                if (endDate != null) {
                    fillInputInOpenForm(page, "section#school",
                            "input[name='school_graduation_dt[]']", endDate);
                    pm.randomDelay(300, 500);
                }

                // 학점 (숨겨져 있음 → btn_desc 클릭으로 표시 → JS로 값 세팅)
                if (edu.getGpa() != null && !edu.getGpa().isBlank()) {
                    setGpaByJs(page, edu.getGpa(), edu.getGpaScale());
                    pm.randomDelay(300, 500);
                }

                // 저장 (evtLayerSave JS 클릭)
                clickSaveButton(page, "section#school", pm);
            }

            result.addSectionSuccess("학력");
            log.info("[SaraminResume] 학력 입력 완료 ({}건)", educations.size());

        } catch (Exception e) {
            log.error("[SaraminResume] 학력 입력 실패: {}", e.getMessage(), e);
            result.addSectionFail("학력", e.getMessage());
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

            for (ResumeCareer career : careers) {
                if (!clickAddButton(page, "section#career", pm)) {
                    result.addSectionFail("경력", "추가 버튼 없음");
                    return;
                }

                // 회사명 (자동완성: type → 드롭다운 → 클릭)
                if (career.getCompanyName() != null) {
                    fillGenericAutocomplete(page, "section#career",
                            "input[name='career_company_nm[]']",
                            career.getCompanyName(), pm);
                }

                // 입사년월
                String startDate = toSaraminDate(career.getStartDate());
                if (startDate != null) {
                    fillInputInOpenForm(page, "section#career",
                            "input[name='career_start[]']", startDate);
                    pm.randomDelay(300, 500);
                }

                // 재직중 또는 퇴사년월
                if (career.isCurrentlyWorking()) {
                    page.evaluate("() => {" +
                            "  const forms = document.querySelectorAll(" +
                            "    \"section#career .resume_edit:not([style*='display:none'])\");" +
                            "  const form = forms[forms.length - 1];" +
                            "  if (!form) return;" +
                            "  const cb = form.querySelector(\"input[id*='careerRetireUi']\");" +
                            "  if (cb && !cb.checked) {" +
                            "    cb.checked = true;" +
                            "    cb.dispatchEvent(new Event('change', {bubbles:true}));" +
                            "    cb.dispatchEvent(new Event('click', {bubbles:true}));" +
                            "  }" +
                            "}");
                    pm.randomDelay(300, 500);
                } else {
                    String endDate = toSaraminDate(career.getEndDate());
                    if (endDate != null) {
                        fillInputInOpenForm(page, "section#career",
                                "input[name='career_end[]']", endDate);
                        pm.randomDelay(300, 500);
                    }
                }

                // 직무 (자동완성: type → btn_auto_keyword.evtReturnAutoComplete 클릭)
                if (career.getPosition() != null) {
                    fillJobCategoryAutocomplete(page, career.getPosition(), pm);
                }

                // 근무부서
                if (career.getDepartment() != null) {
                    fillInputInOpenForm(page, "section#career",
                            "input[name='career_dept_nm[]']", career.getDepartment());
                    pm.randomDelay(300, 500);
                }

                // 담당업무
                if (career.getJobDescription() != null) {
                    fillTextareaInOpenForm(page, "section#career",
                            "textarea[name='career_contents[]']", career.getJobDescription());
                    pm.randomDelay(300, 500);
                }

                // 저장
                clickSaveButton(page, "section#career", pm);
            }

            result.addSectionSuccess("경력");
            log.info("[SaraminResume] 경력 입력 완료 ({}건)", careers.size());

        } catch (Exception e) {
            log.error("[SaraminResume] 경력 입력 실패: {}", e.getMessage(), e);
            result.addSectionFail("경력", e.getMessage());
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

            if (!clickAddButton(page, "section#skill", pm)) {
                result.addSectionFail("스킬", "추가 버튼 없음");
                return;
            }

            for (ResumeSkill skill : skills) {
                String skillName = skill.getSkillName();
                if (skillName == null || skillName.isBlank()) continue;

                // 1순위: 추천 스킬 체크박스 (대소문자 무시 매칭)
                Boolean checked = (Boolean) page.evaluate("(name) => {" +
                        "  const inputs = document.querySelectorAll('.recommend_skill input[data-skill_nm]');" +
                        "  for (const cb of inputs) {" +
                        "    if (cb.getAttribute('data-skill_nm').toLowerCase() === name.toLowerCase()) {" +
                        "      if (!cb.checked) {" +
                        "        const label = cb.nextElementSibling || document.querySelector('label[for=\"' + cb.id + '\"]');" +
                        "        if (label) label.click(); else cb.click();" +
                        "      }" +
                        "      return true;" +
                        "    }" +
                        "  }" +
                        "  return false;" +
                        "}", skillName);

                if (Boolean.TRUE.equals(checked)) {
                    log.info("[SaraminResume] 추천 스킬 체크: {}", skillName);
                    pm.randomDelay(300, 500);
                    continue;
                }

                // 2순위: 검색 → 자동완성 label 클릭
                Locator searchInput = page.locator("section#skill input[name='search_skill']");
                if (searchInput.count() > 0) {
                    searchInput.first().click();
                    searchInput.first().fill("");
                    pm.randomDelay(200, 400);
                    searchInput.first().type(skillName,
                            new Locator.TypeOptions().setDelay(TYPE_DELAY_MS));
                    pm.randomDelay(1500, 2500);

                    // 자동완성 label 클릭 (정확 매칭 우선, 부분 매칭 폴백)
                    Boolean autoChecked = (Boolean) page.evaluate("(name) => {" +
                            "  const items = document.querySelectorAll(" +
                            "    '.list_auto_search li.area_link');" +
                            "  let partial = null;" +
                            "  for (const li of items) {" +
                            "    const label = li.querySelector('label');" +
                            "    const text = label ? label.textContent.trim() : li.textContent.trim();" +
                            "    if (text.toLowerCase() === name.toLowerCase()) {" +
                            "      label ? label.click() : li.querySelector('input')?.click();" +
                            "      return true;" +
                            "    }" +
                            "    if (!partial && text.toLowerCase().includes(name.toLowerCase())) {" +
                            "      partial = label || li.querySelector('input');" +
                            "    }" +
                            "  }" +
                            "  if (partial) { partial.click(); return true; }" +
                            "  return false;" +
                            "}", skillName);

                    if (Boolean.TRUE.equals(autoChecked)) {
                        log.info("[SaraminResume] 스킬 검색 선택: {}", skillName);
                    } else {
                        // 3순위: 직접 등록 (검색어 그대로 등록)
                        Boolean directAdded = (Boolean) page.evaluate("(name) => {" +
                                "  const direct = document.querySelector('.directly_search a, .directly_search button');" +
                                "  if (direct) { direct.click(); return true; }" +
                                "  return false;" +
                                "}", skillName);
                        if (Boolean.TRUE.equals(directAdded)) {
                            log.info("[SaraminResume] 스킬 직접 등록: {}", skillName);
                        } else {
                            log.warn("[SaraminResume] 스킬 매칭 실패 (스킵): {}", skillName);
                        }
                    }
                    pm.randomDelay(300, 500);
                }
            }

            // 저장
            clickSaveButton(page, "section#skill", pm);

            result.addSectionSuccess("스킬");
            log.info("[SaraminResume] 스킬 입력 완료 ({}건)", skills.size());

        } catch (Exception e) {
            log.error("[SaraminResume] 스킬 입력 실패: {}", e.getMessage(), e);
            result.addSectionFail("스킬", e.getMessage());
        }
    }

    // ── 자격증 ──

    private void fillCertifications(Page page, PlaywrightManager pm, Resume resume,
                                    ResumeSyncResult.Builder result) {
        try {
            List<ResumeCertification> certifications = resume.getCertifications();
            if (certifications == null || certifications.isEmpty()) {
                result.addSectionResult("자격증", true, "스킵 (데이터 없음)");
                return;
            }

            navigateToSection(page, "license", pm);

            for (ResumeCertification cert : certifications) {
                if (!clickAddButton(page, "section#license", pm)) {
                    result.addSectionFail("자격증", "추가 버튼 없음");
                    return;
                }

                // license_type → "license" (자격증·면허증)
                selectLicenseType(page, pm, "license");

                // 자격증명
                if (cert.getCertName() != null) {
                    fillGenericAutocomplete(page, "section#license",
                            "input[name='license_nm[]']", cert.getCertName(), pm);
                }

                // 발행처/기관
                if (cert.getIssuingOrganization() != null) {
                    fillInputInOpenForm(page, "section#license",
                            "input[name='license_public_org[]']", cert.getIssuingOrganization());
                    pm.randomDelay(300, 500);
                }

                // 취득일 (YYYYMM)
                String acquiredDate = toSaraminDate(cert.getAcquiredDate());
                if (acquiredDate != null) {
                    fillInputInOpenForm(page, "section#license",
                            "input[name='license_obtain_dt[]']", acquiredDate);
                    pm.randomDelay(300, 500);
                }

                // 합격구분 → "최종합격"
                selectOptionByJs(page, "section#license",
                        "select[name='license_level[]']", "최종합격");
                pm.randomDelay(300, 500);

                clickSaveButton(page, "section#license", pm);
            }

            result.addSectionSuccess("자격증");
            log.info("[SaraminResume] 자격증 입력 완료 ({}건)", certifications.size());

        } catch (Exception e) {
            log.error("[SaraminResume] 자격증 입력 실패: {}", e.getMessage(), e);
            result.addSectionFail("자격증", e.getMessage());
        }
    }

    // ── 어학 ──

    private void fillLanguages(Page page, PlaywrightManager pm, Resume resume,
                               ResumeSyncResult.Builder result) {
        try {
            List<ResumeLanguage> languages = resume.getLanguages();
            if (languages == null || languages.isEmpty()) {
                result.addSectionResult("어학", true, "스킵 (데이터 없음)");
                return;
            }

            navigateToSection(page, "license", pm);

            for (ResumeLanguage lang : languages) {
                if (!clickAddButton(page, "section#license", pm)) {
                    result.addSectionFail("어학", "추가 버튼 없음");
                    return;
                }

                // license_type → "language-exam" (Playwright native selectOption + jQuery trigger)
                selectLicenseType(page, pm, "language-exam");

                // 언어 select (lang_exam_langcode[] - 숫자 코드)
                if (lang.getLanguageName() != null) {
                    String langCode = LANGUAGE_CODE_MAP.getOrDefault(lang.getLanguageName(), "99");
                    Locator langSelect = getOpenForm(page, "section#license")
                            .locator("select[name='lang_exam_langcode[]']");
                    if (langSelect.count() > 0) {
                        langSelect.first().selectOption(langCode);
                        pm.randomDelay(300, 500);
                    }
                }

                // 시험명 (lang_exam_nm[] - type 후 드롭다운 클릭 필수)
                if (lang.getExamName() != null) {
                    Locator examInput = getOpenForm(page, "section#license")
                            .locator("input[name='lang_exam_nm[]']");
                    if (examInput.count() > 0) {
                        examInput.first().click();
                        examInput.first().fill("");
                        pm.randomDelay(200, 400);
                        examInput.first().type(lang.getExamName(),
                                new Locator.TypeOptions().setDelay(TYPE_DELAY_MS));
                        pm.randomDelay(2000, 3000);

                        // 자동완성 드롭다운에서 정확/부분 매칭 클릭
                        Boolean clicked = (Boolean) page.evaluate("(name) => {" +
                                "  const forms = document.querySelectorAll(" +
                                "    \"section#license .resume_edit:not([style*='display:none'])\");" +
                                "  const form = forms[forms.length - 1];" +
                                "  if (!form) return false;" +
                                "  const lis = form.querySelectorAll('.list_auto_search li');" +
                                "  for (const li of lis) {" +
                                "    const text = li.textContent.trim();" +
                                "    if (text.toLowerCase().includes(name.toLowerCase())) {" +
                                "      const a = li.querySelector('a');" +
                                "      if (a) { a.click(); return true; }" +
                                "      li.click(); return true;" +
                                "    }" +
                                "  }" +
                                "  const direct = form.querySelector('.directly_search a, .directly_search button');" +
                                "  if (direct) { direct.click(); return true; }" +
                                "  return false;" +
                                "}", lang.getExamName());

                        if (Boolean.TRUE.equals(clicked)) {
                            log.info("[SaraminResume] 어학 시험명 선택: {}", lang.getExamName());
                        } else {
                            log.warn("[SaraminResume] 어학 시험명 드롭다운 매칭 실패: {}", lang.getExamName());
                        }
                        pm.randomDelay(500, 800);
                    }
                }

                // 점수 (visible dumi + hidden 실제값 둘 다)
                if (lang.getScore() != null) {
                    fillInputInOpenForm(page, "section#license",
                            "input[name='dumi_lang_exam_score[]']", lang.getScore());
                    fillInputInOpenForm(page, "section#license",
                            "input[name='lang_exam_score[]']", lang.getScore());
                    pm.randomDelay(300, 500);
                }

                // 응시일 (lang_exam_obtain_dt[] - YYYYMM)
                String examDate = toSaraminDate(lang.getExamDate());
                if (examDate != null) {
                    fillInputInOpenForm(page, "section#license",
                            "input[name='lang_exam_obtain_dt[]']", examDate);
                    pm.randomDelay(300, 500);
                }

                // 취득여부 → "y" (취득 PASS)
                selectOptionByJs(page, "section#license",
                        "select[name='lang_exam_pass[]']", "y");
                pm.randomDelay(300, 500);

                clickSaveButton(page, "section#license", pm);
            }

            result.addSectionSuccess("어학");
            log.info("[SaraminResume] 어학 입력 완료 ({}건)", languages.size());

        } catch (Exception e) {
            log.error("[SaraminResume] 어학 입력 실패: {}", e.getMessage(), e);
            result.addSectionFail("어학", e.getMessage());
        }
    }

    // ── 경험/활동 ──

    private void fillActivities(Page page, PlaywrightManager pm, Resume resume,
                                ResumeSyncResult.Builder result) {
        try {
            List<ResumeActivity> activities = resume.getActivities();
            if (activities == null || activities.isEmpty()) {
                result.addSectionResult("경험/활동", true, "스킵 (데이터 없음)");
                return;
            }

            navigateToSection(page, "activity", pm);

            for (ResumeActivity activity : activities) {
                if (!clickAddButton(page, "section#activity", pm)) {
                    result.addSectionFail("경험/활동", "추가 버튼 없음");
                    return;
                }

                // 활동구분 select (activity_cd[])
                if (activity.getActivityType() != null) {
                    String cdValue = ACTIVITY_CD_MAP.getOrDefault(
                            activity.getActivityType(), "6");
                    selectOptionByJs(page, "section#activity",
                            "select[name='activity_cd[]']", cdValue);
                    pm.randomDelay(300, 500);
                }

                // 기관/장소명 (activity_org[] - 활동명 + 기관 결합)
                String orgDisplay = buildActivityOrganization(activity);
                if (orgDisplay != null) {
                    fillInputInOpenForm(page, "section#activity",
                            "input[name='activity_org[]']", orgDisplay);
                    pm.randomDelay(300, 500);
                }

                // 시작일 (YYYYMM)
                String startDate = toSaraminDate(activity.getStartDate());
                if (startDate != null) {
                    fillInputInOpenForm(page, "section#activity",
                            "input[name='activity_start[]']", startDate);
                    pm.randomDelay(300, 500);
                }

                // 종료일 (YYYYMM)
                String endDate = toSaraminDate(activity.getEndDate());
                if (endDate != null) {
                    fillInputInOpenForm(page, "section#activity",
                            "input[name='activity_end[]']", endDate);
                    pm.randomDelay(300, 500);
                }

                // 활동내용 (activity_contents[])
                if (activity.getDescription() != null) {
                    fillTextareaInOpenForm(page, "section#activity",
                            "textarea[name='activity_contents[]']", activity.getDescription());
                    pm.randomDelay(300, 500);
                }

                clickSaveButton(page, "section#activity", pm);
            }

            result.addSectionSuccess("경험/활동");
            log.info("[SaraminResume] 경험/활동 입력 완료 ({}건)", activities.size());

        } catch (Exception e) {
            log.error("[SaraminResume] 경험/활동 입력 실패: {}", e.getMessage(), e);
            result.addSectionFail("경험/활동", e.getMessage());
        }
    }

    private String buildActivityOrganization(ResumeActivity activity) {
        String org = activity.getOrganization();
        String name = activity.getActivityName();
        if (org != null && name != null) {
            return name + " / " + org;
        }
        return org != null ? org : name;
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

            // 좌측 메뉴에서 자기소개서(#introduce) 클릭
            page.evaluate("() => {" +
                    "  const link = document.querySelector('a[href=\"#introduce\"]');" +
                    "  if (link) link.click();" +
                    "}");
            pm.shortDelay();

            if (!clickAddButton(page, "section#introduce", pm)) {
                result.addSectionFail("자기소개서", "추가 버튼 없음");
                return;
            }

            // 제목 fill
            fillInputInOpenForm(page, "section#introduce",
                    "input[name='intro_title[]']", "자기소개서");
            pm.randomDelay(300, 500);

            // 내용 fill
            fillTextareaInOpenForm(page, "section#introduce",
                    "textarea[name='intro_contents[]']", selfIntro);
            pm.randomDelay(300, 500);

            // 저장
            clickSaveButton(page, "section#introduce", pm);

            result.addSectionSuccess("자기소개서");
            log.info("[SaraminResume] 자기소개서 입력 완료");

        } catch (Exception e) {
            log.error("[SaraminResume] 자기소개서 입력 실패: {}", e.getMessage(), e);
            result.addSectionFail("자기소개서", e.getMessage());
        }
    }

    // ── 최종 저장 ──

    private void saveResume(Page page, PlaywrightManager pm, ResumeSyncResult.Builder result) {
        try {
            dismissOverlays(page, pm);

            // 1. 포지션 제안 설정 - "적극 구직 중" 라디오 선택
            page.evaluate("() => {" +
                    "  const radio = document.querySelector('#openStatusActive');" +
                    "  if (radio && !radio.checked) {" +
                    "    radio.checked = true;" +
                    "    radio.dispatchEvent(new Event('change', {bubbles:true}));" +
                    "    const label = document.querySelector('label[for=\"openStatusActive\"]');" +
                    "    if (label) label.click();" +
                    "  }" +
                    "}");
            pm.randomDelay(500, 1000);

            // 2. confirm/alert 오버라이드
            page.evaluate("() => {" +
                    "  window.__originalConfirm = window.confirm;" +
                    "  window.__originalAlert = window.alert;" +
                    "  window.confirm = function() { return true; };" +
                    "  window.alert = function() { return; };" +
                    "}");

            // 3. 작성완료 버튼으로 스크롤 + 클릭
            Object clicked = page.evaluate("() => {" +
                    "  const btn = document.querySelector('button.evtResumeSave');" +
                    "  if (!btn) return false;" +
                    "  btn.scrollIntoView({behavior:'instant', block:'center'});" +
                    "  return true;" +
                    "}");

            if (!Boolean.TRUE.equals(clicked)) {
                log.error("[SaraminResume] evtResumeSave 버튼을 찾을 수 없음");
                result.addSectionFail("최종저장", "작성완료 버튼 없음");
                return;
            }

            pm.randomDelay(500, 1000);

            // Playwright 실제 클릭 (JS click 대신)
            Locator saveBtn = page.locator("button.evtResumeSave");
            if (saveBtn.count() > 0) {
                saveBtn.first().click();
                log.info("[SaraminResume] evtResumeSave Playwright 클릭 완료");
            } else {
                // 폴백: JS 클릭
                page.evaluate("() => document.querySelector('button.evtResumeSave')?.click()");
                log.info("[SaraminResume] evtResumeSave JS 클릭 완료");
            }

            pm.shortDelay();
            pm.shortDelay();

            // 4. URL 변경 확인 (페이지 닫힘 = 리다이렉트 성공)
            try {
                // confirm 원복
                page.evaluate("() => {" +
                        "  if (window.__originalConfirm) window.confirm = window.__originalConfirm;" +
                        "  if (window.__originalAlert) window.alert = window.__originalAlert;" +
                        "}");

                String finalUrl = page.url();
                log.info("[SaraminResume] 최종 URL: {}", finalUrl);

                if (!finalUrl.contains("write")) {
                    result.addSectionSuccess("최종저장");
                    log.info("[SaraminResume] 이력서 저장 성공 - 리다이렉트됨: {}", finalUrl);
                } else {
                    Locator errorMsg = page.locator(".error_message, .alert_msg, .toast_msg");
                    if (errorMsg.count() > 0) {
                        String errText = errorMsg.first().textContent();
                        log.error("[SaraminResume] 저장 실패 - 에러 메시지: {}", errText);
                        result.addSectionFail("최종저장", "사람인 에러: " + errText);
                    } else {
                        result.addSectionResult("최종저장", true,
                                "작성완료 클릭됨, 저장 확인 필요");
                    }
                }
            } catch (Exception pageClosedEx) {
                // 페이지가 닫힘 = 작성완료 후 리다이렉트 성공
                result.addSectionSuccess("최종저장");
                log.info("[SaraminResume] 이력서 저장 성공 (페이지 리다이렉트로 컨텍스트 종료)");
            }

        } catch (Exception e) {
            log.error("[SaraminResume] 최종 저장 실패: {}", e.getMessage(), e);
            result.addSectionFail("최종저장", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════
    //  헬퍼 메서드
    // ══════════════════════════════════════════════

    /**
     * license_type select 변경 (Playwright native + jQuery trigger).
     * 자격증/어학/수상 폼 전환에 jQuery 이벤트가 필수이므로 3중 방어.
     */
    private void selectLicenseType(Page page, PlaywrightManager pm, String value) {
        Locator openForm = getOpenForm(page, "section#license");
        Locator typeSelect = openForm.locator("select.evtChangeLicenseType");

        // 1. Playwright native selectOption
        if (typeSelect.count() > 0) {
            typeSelect.first().selectOption(value);
            pm.randomDelay(300, 500);
        }

        // 2. jQuery trigger 보강 (class 기반 셀렉터)
        page.evaluate("(val) => {" +
                "  const forms = document.querySelectorAll(" +
                "    \"section#license .resume_edit:not([style*='display:none'])\");" +
                "  const form = forms[forms.length - 1];" +
                "  if (!form) return;" +
                "  const sel = form.querySelector('select.evtChangeLicenseType');" +
                "  if (!sel) return;" +
                "  sel.value = val;" +
                "  if (typeof $ !== 'undefined') $(sel).val(val).trigger('change');" +
                "  else if (typeof jQuery !== 'undefined') jQuery(sel).val(val).trigger('change');" +
                "  sel.dispatchEvent(new Event('change', {bubbles:true}));" +
                "}", value);
        pm.randomDelay(1000, 1500);
    }

    /**
     * 좌측 메뉴에서 해당 섹션 앵커(#sectionId)를 클릭하여 스크롤 이동한다.
     */
    private void navigateToSection(Page page, String sectionId, PlaywrightManager pm) {
        page.evaluate("(id) => {" +
                "  const link = document.querySelector('a[href=\"#' + id + '\"]');" +
                "  if (link) link.click();" +
                "}", sectionId);
        pm.shortDelay();
        dismissOverlays(page, pm);
    }

    /**
     * 기존 잘못된 항목 정리.
     * 1. confirm/alert 오버라이드
     * 2. 열린 폼 모두 취소 (evtLayerClose)
     * 3. invalid 항목 삭제 (evtDeleteItem)
     */
    private void cleanupExistingItems(Page page, PlaywrightManager pm) {
        try {
            log.info("[SaraminResume] 기존 항목 정리 시작");

            // confirm/alert 자동 수락
            page.evaluate("() => {" +
                    "  window.confirm = function() { return true; };" +
                    "  window.alert = function() { return; };" +
                    "}");

            // 열려있는 모든 폼 취소 (evtLayerClose) - 반복
            for (int i = 0; i < 10; i++) {
                Object closed = page.evaluate("() => {" +
                        "  let count = 0;" +
                        "  document.querySelectorAll('.resume_edit:not([style*=\"display:none\"])').forEach(form => {" +
                        "    const closeBtn = form.querySelector('button.evtLayerClose');" +
                        "    if (closeBtn) { closeBtn.click(); count++; }" +
                        "  });" +
                        "  return count;" +
                        "}");
                if (closed instanceof Number && ((Number) closed).intValue() == 0) break;
                pm.randomDelay(500, 1000);
            }

            pm.randomDelay(500, 1000);

            // invalid 에러가 있는 항목 삭제
            page.evaluate("() => {" +
                    "  document.querySelectorAll('.invalidMsg').forEach(msg => {" +
                    "    const listItem = msg.closest('.list');" +
                    "    if (listItem) {" +
                    "      const deleteBtn = listItem.querySelector('button.evtDeleteItem');" +
                    "      if (deleteBtn) deleteBtn.click();" +
                    "    }" +
                    "  });" +
                    "}");
            pm.randomDelay(500, 1000);

            dismissOverlays(page, pm);
            log.info("[SaraminResume] 기존 항목 정리 완료");

        } catch (Exception e) {
            log.warn("[SaraminResume] 기존 항목 정리 중 오류 (계속 진행): {}", e.getMessage());
        }
    }

    /**
     * 학교명 자동완성.
     * type() → 3초 대기 → li.auto_list a.evtReturnAutoComplete 클릭.
     * 매칭 없으면 "직접 등록하기" 클릭.
     */
    private void fillSchoolAutocomplete(Page page, String schoolName, PlaywrightManager pm) {
        // JS로 학교명 + hidden 필드 직접 세팅 (자동완성 우회)
        page.evaluate("(name) => {" +
                "  const forms = document.querySelectorAll(" +
                "    \"section#school .resume_edit:not([style*='display:none'])\");" +
                "  const form = forms[forms.length - 1];" +
                "  if (!form) return;" +
                // 학교명 input
                "  const input = form.querySelector('input[name=\"school_nm[]\"]');" +
                "  if (input) {" +
                "    input.value = name;" +
                "    input.dispatchEvent(new Event('input', {bubbles:true}));" +
                "    input.dispatchEvent(new Event('change', {bubbles:true}));" +
                "  }" +
                // school_direct = "y" (직접 입력 표시)
                "  const directInput = form.querySelector('input[name=\"school_direct[]\"]');" +
                "  if (directInput) directInput.value = 'y';" +
                // school_cd 비우기 (DB 코드 없음)
                "  const cdInput = form.querySelector('input[name=\"school_cd[]\"]');" +
                "  if (cdInput) cdInput.value = '';" +
                "}", schoolName);
        log.info("[SaraminResume] 학교명 JS 직접 세팅: {}", schoolName);
        pm.randomDelay(500, 1000);
    }

    /**
     * 회사명 등 범용 자동완성.
     * type() → 3초 대기 → .list_auto_search li.auto_list a.evtReturnAutoComplete 클릭.
     * 없으면 "직접 등록하기" 클릭.
     */
    private void fillGenericAutocomplete(Page page, String sectionSelector,
                                         String inputSelector, String value,
                                         PlaywrightManager pm) {
        // JS로 회사명 + hidden 필드 직접 세팅
        page.evaluate("(args) => {" +
                "  const sectionSel = args[0];" +
                "  const inputSel = args[1];" +
                "  const val = args[2];" +
                "  const forms = document.querySelectorAll(" +
                "    sectionSel + \" .resume_edit:not([style*='display:none'])\");" +
                "  const form = forms[forms.length - 1];" +
                "  if (!form) return;" +
                "  const input = form.querySelector(inputSel);" +
                "  if (input) {" +
                "    input.value = val;" +
                "    input.dispatchEvent(new Event('input', {bubbles:true}));" +
                "    input.dispatchEvent(new Event('change', {bubbles:true}));" +
                "  }" +
                "}", new Object[]{sectionSelector, inputSelector, value});
        log.info("[SaraminResume] 회사명 JS 직접 세팅: {}", value);
        pm.randomDelay(500, 1000);
    }

    /**
     * 직무 자동완성.
     * input[name='career_job_category_nm[]'] 타이핑 → btn_auto_keyword.evtReturnAutoComplete 클릭.
     */
    private void fillJobCategoryAutocomplete(Page page, String position, PlaywrightManager pm) {
        try {
            Locator openForm = getOpenForm(page, "section#career");
            Locator input = openForm.locator("input[name='career_job_category_nm[]']");
            if (input.count() == 0) return;

            input.first().click();
            input.first().fill("");
            pm.randomDelay(200, 400);

            input.first().type(position, new Locator.TypeOptions().setDelay(TYPE_DELAY_MS));
            pm.randomDelay(AUTOCOMPLETE_WAIT_MS, AUTOCOMPLETE_WAIT_MS + 1000);

            // Playwright 실제 마우스 클릭
            Locator autoKeywords = page.locator(".btn_auto_keyword.evtReturnAutoComplete");
            if (autoKeywords.count() > 0) {
                autoKeywords.first().click();
                log.info("[SaraminResume] 직무 자동완성 클릭: {}", position);
                pm.randomDelay(500, 1000);
                return;
            }

            log.warn("[SaraminResume] 직무 자동완성 매칭 실패: {}", position);

        } catch (Exception e) {
            log.warn("[SaraminResume] 직무 자동완성 오류 ({}): {}", position, e.getMessage());
        }
    }

    /**
     * 학점을 JS로 세팅한다.
     * btn_desc 클릭 → 숨겨진 div 표시 → input value 세팅 + select value 세팅.
     */
    private void setGpaByJs(Page page, String gpa, String gpaScale) {
        page.evaluate("(args) => {" +
                "  const gpa = args[0];" +
                "  const scale = args[1];" +
                // btn_desc 클릭하여 학점 영역 표시
                "  const forms = document.querySelectorAll(" +
                "    \"section#school .resume_edit:not([style*='display:none'])\");" +
                "  const form = forms[forms.length - 1];" +
                "  if (!form) return;" +
                "  const descBtn = form.querySelector('.btn_desc');" +
                "  if (descBtn) descBtn.click();" +
                // 학점 input 값 세팅
                "  const gpaInputs = form.querySelectorAll('input[name=\"school_major_avg[]\"]');" +
                "  const gpaInput = gpaInputs[gpaInputs.length - 1];" +
                "  if (gpaInput) {" +
                "    const row = gpaInput.closest('.item_row') || gpaInput.closest('div');" +
                "    if (row) row.style.display = '';" +
                "    gpaInput.value = gpa;" +
                "    gpaInput.dispatchEvent(new Event('input', {bubbles:true}));" +
                "    gpaInput.dispatchEvent(new Event('change', {bubbles:true}));" +
                "  }" +
                // 기준학점 select 세팅
                "  if (scale) {" +
                "    const scaleSelects = form.querySelectorAll(" +
                "      'select[name=\"school_major_perfect[]\"]');" +
                "    const scaleSel = scaleSelects[scaleSelects.length - 1];" +
                "    if (scaleSel) {" +
                "      const row = scaleSel.closest('div');" +
                "      if (row) row.style.display = '';" +
                "      scaleSel.value = scale;" +
                "      scaleSel.dispatchEvent(new Event('change', {bubbles:true}));" +
                "    }" +
                "  }" +
                "}", new Object[]{gpa, gpaScale});
    }

    /**
     * JS로 select 값을 세팅하고 change 이벤트를 dispatch 한다.
     * Playwright selectOption()은 jQuery 이벤트를 트리거하지 못하므로 JS로 처리.
     */
    private void selectOptionByJs(Page page, String sectionSelector,
                                  String selectSelector, String value) {
        page.evaluate("(args) => {" +
                "  const sectionSel = args[0];" +
                "  const selectSel = args[1];" +
                "  const val = args[2];" +
                "  const forms = document.querySelectorAll(" +
                "    sectionSel + \" .resume_edit:not([style*='display:none'])\");" +
                "  const form = forms[forms.length - 1];" +
                "  if (!form) return;" +
                "  const sel = form.querySelector(selectSel);" +
                "  if (sel) {" +
                "    sel.value = val;" +
                "    sel.dispatchEvent(new Event('change', {bubbles:true}));" +
                "    if (typeof $ !== 'undefined') $(sel).val(val).trigger('change');" +
                "    else if (typeof jQuery !== 'undefined') jQuery(sel).val(val).trigger('change');" +
                "  }" +
                "}", new Object[]{sectionSelector, selectSelector, value});
    }

    /**
     * 열린 폼 내부의 input에 값을 fill 한다.
     */
    private void fillInputInOpenForm(Page page, String sectionSelector,
                                     String inputSelector, String value) {
        page.evaluate("(args) => {" +
                "  const sectionSel = args[0];" +
                "  const inputSel = args[1];" +
                "  const val = args[2];" +
                "  const forms = document.querySelectorAll(" +
                "    sectionSel + \" .resume_edit:not([style*='display:none'])\");" +
                "  const form = forms[forms.length - 1];" +
                "  if (!form) return;" +
                "  const input = form.querySelector(inputSel);" +
                "  if (input) {" +
                "    input.value = val;" +
                "    input.dispatchEvent(new Event('input', {bubbles:true}));" +
                "    input.dispatchEvent(new Event('change', {bubbles:true}));" +
                "  }" +
                "}", new Object[]{sectionSelector, inputSelector, value});
    }

    /**
     * 열린 폼 내부의 textarea에 값을 fill 한다.
     */
    private void fillTextareaInOpenForm(Page page, String sectionSelector,
                                        String textareaSelector, String value) {
        page.evaluate("(args) => {" +
                "  const sectionSel = args[0];" +
                "  const taSel = args[1];" +
                "  const val = args[2];" +
                "  const forms = document.querySelectorAll(" +
                "    sectionSel + \" .resume_edit:not([style*='display:none'])\");" +
                "  const form = forms[forms.length - 1];" +
                "  if (!form) return;" +
                "  const ta = form.querySelector(taSel);" +
                "  if (ta) {" +
                "    ta.value = val;" +
                "    ta.dispatchEvent(new Event('input', {bubbles:true}));" +
                "    ta.dispatchEvent(new Event('change', {bubbles:true}));" +
                "  }" +
                "}", new Object[]{sectionSelector, textareaSelector, value});
    }

    /**
     * 열린 폼(마지막 .resume_edit:not([style*='display:none']))을 반환한다.
     */
    private Locator getOpenForm(Page page, String sectionSelector) {
        return page.locator(sectionSelector + " .resume_edit:not([style*='display:none'])").last();
    }

    /**
     * JS로 섹션의 "추가" 버튼(btn_add.evtWriteItem)을 클릭한다.
     */
    private boolean clickAddButton(Page page, String sectionSelector, PlaywrightManager pm) {
        Object clicked = page.evaluate("(sel) => {" +
                "  const section = document.querySelector(sel);" +
                "  if (!section) return false;" +
                "  const btn = section.querySelector('button.btn_add.evtWriteItem') || " +
                "              section.querySelector('button.evtWriteItem') || " +
                "              section.querySelector('button.btn_add');" +
                "  if (btn) { btn.click(); return true; }" +
                "  return false;" +
                "}", sectionSelector);
        pm.shortDelay();
        return Boolean.TRUE.equals(clicked);
    }

    /**
     * 섹션 내부의 저장 버튼(evtLayerSave)을 JS로 클릭한다.
     */
    private void clickSaveButton(Page page, String sectionSelector, PlaywrightManager pm) {
        page.evaluate("(sel) => {" +
                "  const section = document.querySelector(sel);" +
                "  if (!section) return;" +
                "  const forms = section.querySelectorAll(" +
                "    '.resume_edit:not([style*=\"display:none\"])');" +
                "  const form = forms[forms.length - 1];" +
                "  if (!form) return;" +
                "  let btn = form.querySelector('button.evtLayerSave');" +
                "  if (!btn) btn = section.querySelector('button.evtLayerSave');" +
                "  if (!btn) {" +
                "    const btns = section.querySelectorAll('button');" +
                "    for (const b of btns) {" +
                "      if (b.textContent.includes('저장')) { btn = b; break; }" +
                "    }" +
                "  }" +
                "  if (btn) btn.click();" +
                "}", sectionSelector);

        pm.shortDelay();
        dismissOverlays(page, pm);
    }

    /**
     * 팝업/오버레이를 모두 닫는다.
     * - .sri_dimmed → display:none
     * - .ModalBox → display:none
     * - [data-dismiss] → click()
     */
    private void dismissOverlays(Page page, PlaywrightManager pm) {
        try {
            page.evaluate("() => {" +
                    "  document.querySelectorAll('.sri_dimmed').forEach(el => el.style.display = 'none');" +
                    "  document.querySelectorAll('.ModalBox').forEach(el => el.style.display = 'none');" +
                    "  document.querySelectorAll('[data-dismiss]').forEach(el => {" +
                    "    try { el.click(); } catch(e) {}" +
                    "  });" +
                    "}");
            pm.randomDelay(500, 1000);
        } catch (Exception e) {
            log.warn("[SaraminResume] 오버레이 제거 중 오류 (무시): {}", e.getMessage());
        }
    }

    /**
     * "2013-03-01" 또는 "201303" → "201303" 변환.
     */
    private String toSaraminDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        String cleaned = dateStr.replace("-", "");
        return cleaned.length() >= 6 ? cleaned.substring(0, 6) : cleaned;
    }
}
