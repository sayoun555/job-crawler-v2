package com.portfolio.jobcrawler.infrastructure.resumesync.importer;

import com.microsoft.playwright.Page;
import com.portfolio.jobcrawler.domain.resume.entity.*;
import com.portfolio.jobcrawler.domain.resume.vo.GraduationStatus;
import com.portfolio.jobcrawler.domain.resume.vo.SchoolType;
import com.portfolio.jobcrawler.infrastructure.crawler.PlaywrightManager;
import com.portfolio.jobcrawler.infrastructure.resumesync.importer.SaraminResumeImporter.ImportResult;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 잡코리아 이력서 작성 페이지의 hidden form 필드에서 데이터를 추출하여 Resume 엔티티로 변환한다.
 */
@Slf4j
@Component
public class JobKoreaResumeImporter {

    private static final String RESUME_WRITE_URL =
            "https://www.jobkorea.co.kr/User/Resume/Write";

    @SuppressWarnings("unchecked")
    public SaraminResumeImporter.ImportResult importResume(Page page, PlaywrightManager pm, Resume resume) {
        log.info("[JobKoreaImporter] 이력서 가져오기 시작");

        try {
            page.navigate(RESUME_WRITE_URL, new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.NETWORKIDLE)
                    .setTimeout(30000));
            pm.shortDelay();

            String currentUrl = page.url();
            if (currentUrl.contains("/Login") || currentUrl.contains("/login")) {
                return SaraminResumeImporter.ImportResult.sessionExpired("잡코리아 로그인 세션이 만료되었습니다.");
            }

            Object rawData = page.evaluate(PARSE_SCRIPT);
            if (!(rawData instanceof Map)) {
                return SaraminResumeImporter.ImportResult.fail("잡코리아 이력서 데이터 파싱 실패");
            }

            Map<String, Object> data = (Map<String, Object>) rawData;
            int imported = 0;

            imported += parseBasicInfo(data, resume);
            imported += parseEducations(data, resume);
            imported += parseCareers(data, resume);
            imported += parseSkills(data, resume);
            imported += parseEducationTraining(data, resume);

            // 이력서 제목 업데이트
            String title = str(data, "resumeTitle");
            if (!title.isEmpty()) {
                resume.updateResumeTitle(title);
            }

            log.info("[JobKoreaImporter] 가져오기 완료 - {}개 항목", imported);
            return SaraminResumeImporter.ImportResult.success(imported);

        } catch (Exception e) {
            log.error("[JobKoreaImporter] 가져오기 실패: {}", e.getMessage(), e);
            return SaraminResumeImporter.ImportResult.fail(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private int parseBasicInfo(Map<String, Object> data, Resume resume) {
        Map<String, String> basic = (Map<String, String>) data.get("basic");
        if (basic == null) return 0;

        String name = basic.getOrDefault("name", "");
        String gender = "False".equals(basic.get("gender")) ? "남" : "여";
        String birth = basic.getOrDefault("birth", "");
        String email = basic.getOrDefault("email", "");
        String phone = basic.getOrDefault("phone", "");
        String address = basic.getOrDefault("address", "");

        resume.updateBasicInfo(name, phone, email, gender, birth, address);
        log.info("[JobKoreaImporter] 기본정보 - 이름:{}", name);
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
            String startDate = str(edu, "startDate");
            String endDate = str(edu, "endDate");
            String gpa = str(edu, "gpa");
            String gpaScale = str(edu, "gpaScale");
            String schoolType = str(edu, "schoolType");
            String gradType = str(edu, "gradType");

            resume.getEducations().add(ResumeEducation.builder()
                    .resume(resume).schoolName(schoolName).major(major)
                    .schoolType(mapSchoolType(schoolType))
                    .graduationStatus(mapGradStatus(gradType))
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

            resume.getCareers().add(ResumeCareer.builder()
                    .resume(resume).companyName(company)
                    .department(str(c, "department"))
                    .position(str(c, "job"))
                    .jobDescription(str(c, "description"))
                    .startDate(str(c, "startDate"))
                    .endDate(str(c, "endDate"))
                    .currentlyWorking("0".equals(str(c, "retireSt")))
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
    private int parseEducationTraining(Map<String, Object> data, Resume resume) {
        List<Map<String, Object>> trainings = (List<Map<String, Object>>) data.get("trainings");
        if (trainings == null) return 0;

        int count = 0;
        for (Map<String, Object> t : trainings) {
            String name = str(t, "name");
            if (name.isEmpty()) continue;

            resume.getActivities().add(ResumeActivity.builder()
                    .resume(resume)
                    .activityName(name)
                    .organization(str(t, "institution"))
                    .description(str(t, "content"))
                    .startDate(str(t, "startDate"))
                    .endDate(str(t, "endDate"))
                    .sortOrder(count).build());
            count++;
        }
        return count;
    }

    private SchoolType mapSchoolType(String code) {
        return switch (code) {
            case "1" -> SchoolType.COLLEGE_2Y;
            case "2" -> SchoolType.COLLEGE_4Y;
            case "3" -> SchoolType.GRADUATE_MASTER;
            case "4" -> SchoolType.GRADUATE_DOCTOR;
            default -> SchoolType.COLLEGE_4Y;
        };
    }

    private GraduationStatus mapGradStatus(String code) {
        return switch (code) {
            case "10" -> GraduationStatus.GRADUATED;
            case "20" -> GraduationStatus.ENROLLED;
            case "50" -> GraduationStatus.EXPECTED;
            case "30" -> GraduationStatus.LEAVE_OF_ABSENCE;
            case "40" -> GraduationStatus.DROPPED;
            default -> GraduationStatus.GRADUATED;
        };
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString().trim() : "";
    }

    /**
     * 잡코리아 이력서 작성 페이지의 hidden form 필드에서 구조화된 데이터를 추출하는 JS 스크립트.
     */
    private static final String PARSE_SCRIPT = """
            () => {
              const result = {};
              const val = (id) => { const el = document.getElementById(id); return el ? (el.value || '').trim() : ''; };

              result.resumeTitle = val('UserResume_M_Resume_Title');

              // 기본정보
              result.basic = {
                name: val('UserInfo_M_Name'),
                birth: val('UserInfo_M_Born'),
                gender: val('UserInfo_M_Gender'),
                email: val('UserInfo_M_Email'),
                phone: val('UserInfo_M_Hand_Phone'),
                address: (val('UserInfo_M_Addr_Text') + ' ' + val('UserInfo_M_Addr_Text1')).trim()
              };

              // 학력 - UnivSchool[cXX] 패턴
              result.educations = [];
              document.querySelectorAll('input[name="UnivSchool.index"]').forEach(indexEl => {
                const idx = indexEl.value;
                const schoolName = val('') || (document.querySelector(`input[name="UnivSchool[${idx}].Schl_Name"]`)?.value || '');
                if (!schoolName) return;
                const majorEl = document.querySelector(`input[name="UnivSchool[${idx}].UnivMajor[0].Major_Name"]`);
                result.educations.push({
                  schoolName: schoolName,
                  major: majorEl ? majorEl.value : '',
                  schoolType: document.querySelector(`input[name="UnivSchool[${idx}].Schl_Type_Code"][type="hidden"]:not([id=""])`)?.value || '',
                  gradType: val(`UnivSchool_Grad_Type_Code_${idx}`),
                  startDate: val(`UnivSchool_Entc_YM_${idx}`),
                  endDate: val(`UnivSchool_Grad_YM_${idx}`),
                  gpa: val(`UnivSchool_Grade_${idx}`),
                  gpaScale: val(`UnivSchool_Grade_Prft_Scr_${idx}`)
                });
              });

              // 경력 - Career[cXX] 패턴
              result.careers = [];
              document.querySelectorAll('input[name="Career.index"]').forEach(indexEl => {
                const idx = indexEl.value;
                const companyEl = document.querySelector(`input[name="Career[${idx}].C_Name"]`);
                if (!companyEl || !companyEl.value) return;
                result.careers.push({
                  companyName: companyEl.value,
                  department: val(`Career_C_Part_${idx}`),
                  startDate: val(`Career_CSYM_${idx}`),
                  endDate: val(`Career_CEYM_${idx}`),
                  retireSt: document.querySelector(`input[name="Career[${idx}].RetireSt"]`)?.value || '1',
                  job: document.querySelector(`input[name="Career[${idx}].M_MainJob"]`)?.value || '',
                  description: val(`Career_Prfm_Prt_${idx}`)
                });
              });

              // 스킬 - Skill[cXX].Skill_Name 패턴
              result.skills = [];
              document.querySelectorAll('input[name$="].Skill_Name"]').forEach(el => {
                if (el.value) result.skills.push(el.value);
              });

              // 교육이수 - Edu[cXX] 패턴
              result.trainings = [];
              document.querySelectorAll('input[name="Edu.Index"]').forEach(indexEl => {
                const idx = indexEl.value;
                result.trainings.push({
                  name: val(`Edu_Edu_Name_${idx}`),
                  institution: document.querySelector(`input[name="Edu[${idx}].Edu_Inst_Name"]`)?.value || '',
                  startDate: val(`Edu_Edu_Start_YM_${idx}`),
                  endDate: val(`Edu_Edu_End_YM_${idx}`),
                  content: val(`Edu_Edu_Cntnt_${idx}`)
                });
              });

              return result;
            }
            """;
}
