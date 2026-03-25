package com.portfolio.jobcrawler.infrastructure.resumesync.importer;

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
 * 사람인 이력서 편집 페이지의 hidden form 필드에서 데이터를 추출하여 Resume 엔티티로 변환한다.
 */
@Slf4j
@Component
public class SaraminResumeImporter {

    private static final String RESUME_MANAGE_URL =
            "https://www.saramin.co.kr/zf_user/resume/resume-manage";

    @SuppressWarnings("unchecked")
    public ImportResult importResume(Page page, PlaywrightManager pm, Resume resume) {
        log.info("[SaraminImporter] 이력서 가져오기 시작");

        try {
            String editUrl = findResumeEditUrl(page, pm);
            if (editUrl == null) {
                return ImportResult.fail("사람인에 등록된 이력서가 없습니다.");
            }

            page.navigate(editUrl, new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.NETWORKIDLE)
                    .setTimeout(30000));
            pm.shortDelay();

            String currentUrl = page.url();
            if (currentUrl.contains("/login") || currentUrl.contains("/auth")) {
                return ImportResult.sessionExpired("사람인 로그인 세션이 만료되었습니다.");
            }

            Object rawData = page.evaluate(PARSE_SCRIPT);
            if (!(rawData instanceof Map)) {
                return ImportResult.fail("이력서 데이터 파싱 실패");
            }

            Map<String, Object> data = (Map<String, Object>) rawData;
            int imported = 0;

            imported += parseBasicInfo(data, resume);
            imported += parseEducations(data, resume);
            imported += parseCareers(data, resume);
            imported += parseSkills(data, resume);
            imported += parseActivities(data, resume);
            imported += parseIntroductions(data, resume);

            log.info("[SaraminImporter] 가져오기 완료 - {}개 항목", imported);
            return ImportResult.success(imported);

        } catch (Exception e) {
            log.error("[SaraminImporter] 가져오기 실패: {}", e.getMessage(), e);
            return ImportResult.fail(e.getMessage());
        }
    }

    private String findResumeEditUrl(Page page, PlaywrightManager pm) {
        try {
            page.navigate(RESUME_MANAGE_URL, new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.NETWORKIDLE)
                    .setTimeout(30000));
            pm.shortDelay();

            String currentUrl = page.url();
            if (currentUrl.contains("/login") || currentUrl.contains("/auth")) {
                log.error("[SaraminImporter] 로그인 세션 만료");
                return null;
            }

            // 수정하기 버튼의 data-action="edit" 또는 res_idx 링크에서 추출
            Object resIdx = page.evaluate("() => {" +
                    "  const editBtn = document.querySelector('button[data-action=\"edit\"]');" +
                    "  if (editBtn) {" +
                    "    const li = editBtn.closest('li') || editBtn.closest('[data-res_idx]');" +
                    "    if (li && li.dataset.res_idx) return li.dataset.res_idx;" +
                    "  }" +
                    "  const a = document.querySelector('a[href*=\"res_idx\"]');" +
                    "  if (a) {" +
                    "    const m = a.href.match(/res_idx[/=](\\d+)/);" +
                    "    if (m) return m[1];" +
                    "  }" +
                    "  const link = document.querySelector('a[href*=\"resume-manage/edit\"]');" +
                    "  if (link) {" +
                    "    const m = link.href.match(/res_idx=(\\d+)/);" +
                    "    if (m) return m[1];" +
                    "  }" +
                    "  return null;" +
                    "}");

            if (resIdx instanceof String) {
                String editUrl = "https://www.saramin.co.kr/zf_user/member/resume-manage/edit?res_idx=" + resIdx;
                log.info("[SaraminImporter] 이력서 발견: res_idx={}", resIdx);
                return editUrl;
            }

            log.warn("[SaraminImporter] 이력서 목록에서 res_idx를 찾을 수 없음");
            return null;
        } catch (Exception e) {
            log.warn("[SaraminImporter] 이력서 목록 조회 실패: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private int parseBasicInfo(Map<String, Object> data, Resume resume) {
        Map<String, String> basic = (Map<String, String>) data.get("basic");
        if (basic == null) return 0;

        String name = basic.getOrDefault("name", "");
        String gender = "male".equals(basic.get("gender")) ? "남" : "female".equals(basic.get("gender")) ? "여" : "";
        String birth = basic.getOrDefault("birth", "");
        String email = basic.getOrDefault("email", "");
        String phone = basic.getOrDefault("phone", "");
        String address = basic.getOrDefault("address", "");

        // 생년월일 포맷 변환 (19940523 → 1994-05-23)
        if (birth.length() == 8) {
            birth = birth.substring(0, 4) + "-" + birth.substring(4, 6) + "-" + birth.substring(6, 8);
        }

        resume.updateBasicInfo(name, phone, email, gender, birth, address);
        log.info("[SaraminImporter] 기본정보 파싱 - 이름:{}, 성별:{}", name, gender);
        return 1;
    }

    @SuppressWarnings("unchecked")
    private int parseEducations(Map<String, Object> data, Resume resume) {
        List<Map<String, Object>> educations = (List<Map<String, Object>>) data.get("educations");
        if (educations == null) return 0;

        resume.getEducations().clear();
        int count = 0;
        for (Map<String, Object> edu : educations) {
            String schoolName = str(edu, "schoolName");
            if (schoolName.isEmpty()) continue;

            String major = str(edu, "major");
            String startDt = str(edu, "startDate");
            String endDt = str(edu, "endDate");
            String gradGb = str(edu, "graduationGb");
            String gpa = str(edu, "gpa");
            String gpaScale = str(edu, "gpaScale");
            String schoolGb = str(edu, "schoolGb");

            // YYYYMM → YYYY-MM-01
            String startDate = formatYearMonth(startDt);
            String endDate = formatYearMonth(endDt);

            SchoolType schoolType = mapSchoolType(schoolGb);
            GraduationStatus gradStatus = mapGraduationStatus(gradGb);

            resume.getEducations().add(ResumeEducation.builder()
                    .resume(resume).schoolName(schoolName).major(major)
                    .schoolType(schoolType).graduationStatus(gradStatus)
                    .startDate(startDate).endDate(endDate)
                    .gpa(gpa).gpaScale(gpaScale)
                    .sortOrder(count).build());
            count++;
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int parseCareers(Map<String, Object> data, Resume resume) {
        List<Map<String, Object>> careers = (List<Map<String, Object>>) data.get("careers");
        if (careers == null) return 0;

        resume.getCareers().clear();
        int count = 0;
        for (Map<String, Object> c : careers) {
            String company = str(c, "companyName");
            if (company.isEmpty()) continue;

            String startDt = str(c, "startDate");
            String endDt = str(c, "endDate");
            String position = str(c, "position");
            String department = str(c, "department");
            String description = str(c, "description");
            boolean currentlyWorking = "n".equals(str(c, "retireFl"));

            resume.getCareers().add(ResumeCareer.builder()
                    .resume(resume).companyName(company)
                    .position(position).department(department)
                    .jobDescription(description)
                    .startDate(formatYearMonth(startDt))
                    .endDate(formatYearMonth(endDt))
                    .currentlyWorking(currentlyWorking)
                    .sortOrder(count).build());
            count++;
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int parseSkills(Map<String, Object> data, Resume resume) {
        List<String> skills = (List<String>) data.get("skills");
        if (skills == null) return 0;

        resume.getSkills().clear();
        int count = 0;
        for (String skill : skills) {
            if (skill == null || skill.isBlank()) continue;
            resume.getSkills().add(ResumeSkill.builder()
                    .resume(resume).skillName(skill.trim())
                    .sortOrder(count).build());
            count++;
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int parseActivities(Map<String, Object> data, Resume resume) {
        List<Map<String, Object>> activities = (List<Map<String, Object>>) data.get("activities");
        if (activities == null) return 0;

        resume.getActivities().clear();
        int count = 0;
        for (Map<String, Object> act : activities) {
            String org = str(act, "organization");
            if (org.isEmpty()) continue;

            String description = str(act, "description");
            String startDt = str(act, "startDate");
            String endDt = str(act, "endDate");
            String typeCd = str(act, "typeCd");

            resume.getActivities().add(ResumeActivity.builder()
                    .resume(resume).activityName(org)
                    .organization(org).description(description)
                    .activityType(mapActivityType(typeCd))
                    .startDate(formatYearMonth(startDt))
                    .endDate(formatYearMonth(endDt))
                    .sortOrder(count).build());
            count++;
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int parseIntroductions(Map<String, Object> data, Resume resume) {
        List<Map<String, Object>> intros = (List<Map<String, Object>>) data.get("introductions");
        if (intros == null || intros.isEmpty()) return 0;

        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> intro : intros) {
            String title = str(intro, "title");
            String content = str(intro, "content");
            if (!title.isEmpty()) sb.append("[").append(title).append("]\n");
            if (!content.isEmpty()) sb.append(content).append("\n\n");
        }
        if (!sb.isEmpty()) {
            resume.updateSelfIntroduction(sb.toString().trim());
            return 1;
        }
        return 0;
    }

    private String formatYearMonth(String yyyymm) {
        if (yyyymm == null || yyyymm.length() < 6) return null;
        return yyyymm.substring(0, 4) + "-" + yyyymm.substring(4, 6) + "-01";
    }

    private SchoolType mapSchoolType(String schoolGb) {
        return switch (schoolGb) {
            case "college" -> SchoolType.COLLEGE_2Y;
            case "university" -> SchoolType.COLLEGE_4Y;
            case "master" -> SchoolType.GRADUATE_MASTER;
            case "doctor" -> SchoolType.GRADUATE_DOCTOR;
            default -> SchoolType.COLLEGE_4Y;
        };
    }

    private GraduationStatus mapGraduationStatus(String gradGb) {
        return switch (gradGb) {
            case "1" -> GraduationStatus.GRADUATED;
            case "2" -> GraduationStatus.ENROLLED;
            case "3" -> GraduationStatus.LEAVE_OF_ABSENCE;
            case "4" -> GraduationStatus.COMPLETED;
            case "5" -> GraduationStatus.DROPPED;
            case "6" -> GraduationStatus.EXPECTED;
            default -> null;
        };
    }

    private ActivityType mapActivityType(String typeCd) {
        return switch (typeCd) {
            case "1" -> ActivityType.SCHOOL_ACTIVITY;
            case "2" -> ActivityType.INTERN;
            case "3" -> ActivityType.VOLUNTEER;
            case "4" -> ActivityType.CLUB;
            case "5" -> ActivityType.PART_TIME;
            case "6" -> ActivityType.EXTERNAL_ACTIVITY;
            case "education" -> ActivityType.EDUCATION;
            case "abroad" -> ActivityType.OVERSEAS;
            default -> ActivityType.EXTERNAL_ACTIVITY;
        };
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString().trim() : "";
    }

    /**
     * 사람인 이력서 편집 페이지의 hidden form 필드에서 구조화된 데이터를 추출하는 JS 스크립트.
     */
    private static final String PARSE_SCRIPT = """
            () => {
              const result = {};
              const val = (sel) => { const el = document.querySelector(sel); return el ? el.value || '' : ''; };
              const vals = (name) => Array.from(document.querySelectorAll(`input[name="${name}"], textarea[name="${name}"], select[name="${name}"]`)).map(e => e.value || '');

              // 기본정보
              result.basic = {
                name: val('input[name="user_nm"]'),
                gender: val('select[name="sex"]'),
                birth: val('input[name="user_birth"]'),
                email: document.querySelector('li.user_email')?.textContent?.trim() || val('input[name="enc_email"]'),
                phone: document.querySelector('li.user_phone')?.textContent?.trim() || val('input[name="enc_cell"]'),
                address: (val('input[name="old_address"]') + ' ' + val('input[name="old_address_details"]')).trim()
              };

              // 학력 (hidden form 필드)
              const schoolNames = vals('school_nm[]');
              const schoolMajors = vals('school_major[]');
              const schoolGbs = vals('school_gb[]');
              const schoolGradGbs = vals('school_graduation_gb[]');
              const schoolStartDts = vals('school_entrance_dt[]');
              const schoolGradDts = vals('school_graduation_dt[]');
              const schoolGpas = vals('school_major_avg[]');
              const schoolGpaScales = vals('school_major_perfect[]');
              result.educations = schoolNames.map((name, i) => ({
                schoolName: name,
                major: schoolMajors[i] || '',
                schoolGb: schoolGbs[i] || '',
                graduationGb: schoolGradGbs[i] || '',
                startDate: schoolStartDts[i] || '',
                endDate: schoolGradDts[i] || '',
                gpa: schoolGpas[i] || '',
                gpaScale: schoolGpaScales[i] || ''
              }));

              // 경력 (hidden form 필드)
              const careerCompanies = vals('career_company_nm[]');
              const careerStarts = vals('career_start[]');
              const careerEnds = vals('career_end[]');
              const careerPositions = vals('career_job_category_nm[]');
              const careerDepts = vals('career_dept_nm[]');
              const careerContents = vals('career_contents[]');
              const careerRetireFls = vals('career_retire_fl[]');
              result.careers = careerCompanies.map((company, i) => ({
                companyName: company,
                startDate: careerStarts[i] || '',
                endDate: careerEnds[i] || '',
                position: careerPositions[i] || '',
                department: careerDepts[i] || '',
                description: careerContents[i] || '',
                retireFl: careerRetireFls[i] || 'y'
              }));

              // 스킬 (hidden form 필드)
              result.skills = vals('s_ability_gb[]').filter(s => s.length > 0);

              // 경험/활동 (hidden form 필드)
              const actOrgs = vals('activity_org[]');
              const actCds = vals('activity_cd[]');
              const actStarts = vals('activity_start[]');
              const actEnds = vals('activity_end[]');
              const actContents = vals('activity_contents[]');
              result.activities = actOrgs.map((org, i) => ({
                organization: org,
                typeCd: actCds[i] || '',
                startDate: actStarts[i] || '',
                endDate: actEnds[i] || '',
                description: actContents[i] || ''
              }));

              // 자기소개서 (보이는 리스트에서 추출)
              result.introductions = [];
              document.querySelectorAll('section#introduce li.list').forEach(li => {
                const h3 = li.querySelector('h3.tit');
                const desc = li.querySelector('p.desc');
                result.introductions.push({
                  title: h3 ? h3.textContent.trim() : '',
                  content: desc ? desc.textContent.trim() : ''
                });
              });
              // 자기소개서가 없으면 textarea에서
              if (result.introductions.length === 0) {
                const ta = document.querySelector('section#introduce textarea');
                if (ta && ta.value) {
                  result.introductions.push({ title: '', content: ta.value });
                }
              }

              return result;
            }
            """;

    public record ImportResult(boolean success, boolean sessionExpired, String message, int importedCount) {
        public static ImportResult success(int count) {
            return new ImportResult(true, false, count + "개 항목 가져오기 완료", count);
        }
        public static ImportResult fail(String message) {
            return new ImportResult(false, false, message, 0);
        }
        public static ImportResult sessionExpired(String message) {
            return new ImportResult(false, true, message, 0);
        }
    }
}
