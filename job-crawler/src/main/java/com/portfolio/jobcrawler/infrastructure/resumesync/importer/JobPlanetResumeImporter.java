package com.portfolio.jobcrawler.infrastructure.resumesync.importer;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.portfolio.jobcrawler.domain.resume.entity.*;
import com.portfolio.jobcrawler.domain.resume.vo.SchoolType;
import com.portfolio.jobcrawler.infrastructure.crawler.PlaywrightManager;
import com.portfolio.jobcrawler.infrastructure.resumesync.importer.SaraminResumeImporter.ImportResult;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 잡플래닛 이력서를 Playwright DOM에서 직접 추출한다.
 * 잡플래닛은 SPA(React)이므로 hidden form 대신 렌더된 DOM 텍스트를 파싱한다.
 */
@Slf4j
@Component
public class JobPlanetResumeImporter {

    private static final String RESUME_EDIT_URL =
            "https://www.jobplanet.co.kr/users/resume/%s/edit";
    private static final String RESUME_LIST_URL =
            "https://www.jobplanet.co.kr/profile/resumes";

    @SuppressWarnings("unchecked")
    public SaraminResumeImporter.ImportResult importResume(Page page, PlaywrightManager pm, Resume resume) {
        log.info("[JobPlanetImporter] 이력서 가져오기 시작");

        try {
            // 1. 이력서 목록에서 ID 추출
            page.navigate(RESUME_LIST_URL, new Page.NavigateOptions().setTimeout(30000));
            pm.longDelay();

            String currentUrl = page.url();
            if (currentUrl.contains("/sign_in") || currentUrl.contains("/login")) {
                return SaraminResumeImporter.ImportResult.sessionExpired("잡플래닛 로그인 세션이 만료되었습니다.");
            }

            // API로 이력서 목록 조회
            Object listData = page.evaluate("""
                    async () => {
                        const res = await fetch('/api/v5/resumes', { credentials: 'include' });
                        if (!res.ok) return null;
                        return await res.json();
                    }
                    """);

            if (!(listData instanceof Map)) {
                return SaraminResumeImporter.ImportResult.fail("잡플래닛 이력서 목록 조회 실패");
            }

            Map<String, Object> listResult = (Map<String, Object>) listData;
            Map<String, Object> data = (Map<String, Object>) listResult.get("data");
            List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");

            if (items == null || items.isEmpty()) {
                return SaraminResumeImporter.ImportResult.fail("잡플래닛에 등록된 이력서가 없습니다.");
            }

            // 첫 번째(대표) 이력서 ID
            Object resumeId = items.get(0).get("id");

            // 2. 이력서 카드 클릭하여 편집 페이지 진입 (SPA 내부 라우팅)
            try {
                page.locator("li[class*='1t1ow3r'], li[class*='resume'] div[class*='qxs6rj']").first().click();
                pm.longDelay();
                pm.longDelay(); // SPA 렌더링 충분히 대기
            } catch (Exception e) {
                log.warn("[JobPlanetImporter] 이력서 카드 클릭 실패, URL 직접 접근 시도");
                String editUrl = String.format(RESUME_EDIT_URL, resumeId);
                page.navigate(editUrl, new Page.NavigateOptions().setTimeout(60000));
                pm.longDelay();
                pm.longDelay();
            }

            // SPA가 로드될 때까지 특정 요소 대기
            try {
                page.waitForSelector(".input_resume_name_kr, .rsm_wrap, .tag_group",
                        new Page.WaitForSelectorOptions().setTimeout(15000));
                log.info("[JobPlanetImporter] SPA 렌더링 완료: {}", page.url());
            } catch (Exception e) {
                log.warn("[JobPlanetImporter] SPA 렌더링 대기 타임아웃, DOM 추출 시도");
            }

            Object rawData = page.evaluate(PARSE_SCRIPT);
            if (!(rawData instanceof Map)) {
                return SaraminResumeImporter.ImportResult.fail("잡플래닛 이력서 데이터 파싱 실패");
            }

            Map<String, Object> resumeResult = (Map<String, Object>) rawData;
            log.info("[JobPlanetImporter] 추출 결과: basic={}, skills={}, careers={}, educations={}",
                    resumeResult.get("basic") != null,
                    ((List<?>) resumeResult.getOrDefault("skills", List.of())).size(),
                    ((List<?>) resumeResult.getOrDefault("careers", List.of())).size(),
                    ((List<?>) resumeResult.getOrDefault("educations", List.of())).size());
            int imported = 0;

            imported += parseBasicInfo(resumeResult, resume);
            imported += parseEducations(resumeResult, resume);
            imported += parseCareers(resumeResult, resume);
            imported += parseSkills(resumeResult, resume);

            log.info("[JobPlanetImporter] 가져오기 완료 - {}개 항목", imported);
            return SaraminResumeImporter.ImportResult.success(imported);

        } catch (Exception e) {
            log.error("[JobPlanetImporter] 가져오기 실패: {}", e.getMessage(), e);
            return SaraminResumeImporter.ImportResult.fail(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private int parseBasicInfo(Map<String, Object> data, Resume resume) {
        Map<String, String> basic = (Map<String, String>) data.get("basic");
        if (basic == null) return 0;
        resume.updateBasicInfo(
                basic.getOrDefault("name", ""),
                basic.getOrDefault("phone", ""),
                basic.getOrDefault("email", ""),
                "", "", "");
        return 1;
    }

    @SuppressWarnings("unchecked")
    private int parseEducations(Map<String, Object> data, Resume resume) {
        List<Map<String, Object>> educations = (List<Map<String, Object>>) data.get("educations");
        if (educations == null) return 0;
        resume.getEducations().clear();
        int count = 0;
        for (Map<String, Object> edu : educations) {
            String name = str(edu, "name");
            if (name.isEmpty()) continue;
            resume.getEducations().add(ResumeEducation.builder()
                    .resume(resume).schoolName(name).major(str(edu, "major"))
                    .schoolType(SchoolType.COLLEGE_4Y)
                    .startDate(str(edu, "startDate")).endDate(str(edu, "endDate"))
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
            String company = str(c, "company");
            if (company.isEmpty()) continue;
            resume.getCareers().add(ResumeCareer.builder()
                    .resume(resume).companyName(company)
                    .position(str(c, "position"))
                    .startDate(str(c, "startDate")).endDate(str(c, "endDate"))
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

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString().trim() : "";
    }

    /**
     * 잡플래닛 이력서 편집 페이지에서 데이터를 추출하는 JS.
     * 실제 HTML 구조: .input_resume_name_kr, .tag_group .tagtext, .rsm_career 등
     */
    private static final String PARSE_SCRIPT = """
            () => {
              const result = { basic: {}, educations: [], careers: [], skills: [] };

              // 기본정보
              const nameEl = document.querySelector('.input_resume_name_kr');
              if (nameEl) result.basic.name = nameEl.value;
              const phoneEl = document.querySelector('.input_resume_phone');
              if (phoneEl) result.basic.phone = phoneEl.value;
              const emailHidden = document.querySelector('input[name="email[email]"]');
              if (emailHidden) result.basic.email = emailHidden.value;
              const emailInput = document.querySelector('.input_resume_email');
              if (emailInput && emailInput.value) result.basic.email = emailInput.value;

              // 스킬 태그 (.tag_group .tagtext)
              document.querySelectorAll('.tag_group .tagtext').forEach(el => {
                // 삭제 아이콘 텍스트 제외
                const text = el.childNodes[0]?.textContent?.trim();
                if (text && text.length > 0) result.skills.push(text);
              });

              // 추천 스킬 중 active인 것도 추가
              document.querySelectorAll('.resume-recommend-skill__item.active').forEach(el => {
                const text = el.textContent.trim();
                if (text && !result.skills.includes(text)) result.skills.push(text);
              });

              // 경력 (.rsm_career #rsmExperiences 안의 .flexible_resume_row)
              const expSection = document.getElementById('rsmExperiences');
              if (expSection) {
                expSection.querySelectorAll('.flexible_resume_row').forEach(row => {
                  const companyEl = row.querySelector('.input_resume_text1');
                  const company = companyEl ? companyEl.value : '';
                  if (!company) return;

                  const dateInputs = row.querySelectorAll('.input_resume');
                  const startDate = dateInputs[0]?.value || '';
                  const endDate = dateInputs[1]?.value || '';
                  const deptEl = row.querySelectorAll('.input_resume_text3');
                  const dept = deptEl[0]?.value || '';
                  const position = deptEl[1]?.value || '';
                  const descEl = row.querySelector('.medit');
                  const desc = descEl ? descEl.value : '';

                  result.careers.push({ company, startDate, endDate, position, department: dept, description: desc });
                });
              }

              // 학력 (학력 섹션의 .flexible_resume_row)
              document.querySelectorAll('.rsm_section.rsm_career').forEach(section => {
                const heading = section.querySelector('.rsm_ttl');
                if (!heading || !heading.textContent.includes('학력')) return;
                section.querySelectorAll('.flexible_resume_row').forEach(row => {
                  const schoolEl = row.querySelector('.input_resume_text1');
                  const school = schoolEl ? schoolEl.value : '';
                  if (!school) return;
                  const majorEl = row.querySelector('.input_resume_text2');
                  const dateInputs = row.querySelectorAll('.input_resume');
                  result.educations.push({
                    name: school,
                    major: majorEl ? majorEl.value : '',
                    startDate: dateInputs[0]?.value || '',
                    endDate: dateInputs[1]?.value || ''
                  });
                });
              });

              return result;
            }
            """;
}
