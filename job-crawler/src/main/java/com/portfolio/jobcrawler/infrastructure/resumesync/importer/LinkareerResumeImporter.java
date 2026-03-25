package com.portfolio.jobcrawler.infrastructure.resumesync.importer;

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
 * 링커리어 이력서(스펙 관리)를 Playwright DOM에서 추출한다.
 * 링커리어는 React SPA이므로 렌더된 DOM 텍스트와 input 필드에서 파싱한다.
 */
@Slf4j
@Component
public class LinkareerResumeImporter {

    private static final String SPEC_URL =
            "https://linkareer.com/my-career/spec-management?section=info";

    @SuppressWarnings("unchecked")
    public SaraminResumeImporter.ImportResult importResume(Page page, PlaywrightManager pm, Resume resume) {
        log.info("[LinkareerImporter] 이력서 가져오기 시작");

        try {
            page.navigate(SPEC_URL, new Page.NavigateOptions().setTimeout(30000));
            pm.longDelay();

            String currentUrl = page.url();
            if (currentUrl.contains("/login") || currentUrl.contains("/signin")) {
                return SaraminResumeImporter.ImportResult.sessionExpired("링커리어 로그인 세션이 만료되었습니다.");
            }

            // 기본정보 섹션에서 추출
            Object rawData = page.evaluate(PARSE_INFO_SCRIPT);
            if (!(rawData instanceof Map)) {
                return SaraminResumeImporter.ImportResult.fail("링커리어 기본정보 파싱 실패");
            }

            Map<String, Object> data = (Map<String, Object>) rawData;
            int imported = parseBasicInfo(data, resume);

            // 희망근무조건에서 세부직무/근무지역 추출
            page.navigate("https://linkareer.com/my-career/spec-management?section=workingCondition",
                    new Page.NavigateOptions().setTimeout(30000));
            pm.longDelay();
            Object conditionData = page.evaluate(PARSE_WORKING_CONDITION_SCRIPT);
            if (conditionData instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cData = (Map<String, Object>) conditionData;
                imported += parseDesiredConditions(cData, resume);
            }

            // 각 섹션으로 이동하면서 main 영역 안의 실제 데이터만 추출
            String[] sections = {"education", "career", "skills"};
            for (String section : sections) {
                page.navigate("https://linkareer.com/my-career/spec-management?section=" + section,
                        new Page.NavigateOptions().setTimeout(30000));
                pm.longDelay();

                // main 영역 안의 input 값 + DOM 텍스트 디버그 로깅
                Object debugInputs = page.evaluate("""
                    () => {
                        const main = document.querySelector('main');
                        if (!main) return [];
                        return Array.from(main.querySelectorAll('input, textarea, select')).map(el => ({
                            tag: el.tagName,
                            type: el.type || '',
                            name: el.name || '',
                            placeholder: el.placeholder || '',
                            value: (el.value || '').substring(0, 80),
                            options: el.tagName === 'SELECT' ? Array.from(el.options).filter(o => o.selected).map(o => o.text) : []
                        })).filter(i => i.value || i.options.length > 0);
                    }
                """);
                log.info("[LinkareerImporter] {} 입력필드: {}", section, debugInputs);

                Object sectionData = page.evaluate(PARSE_MAIN_CONTENT_SCRIPT);
                if (!(sectionData instanceof Map)) continue;

                Map<String, Object> sData = (Map<String, Object>) sectionData;
                String sectionName = str(sData, "section");
                log.info("[LinkareerImporter] {} 섹션 - 항목 {}개, 입력필드 {}개",
                        section, ((List<?>) sData.getOrDefault("items", List.of())).size(),
                        ((List<?>) sData.getOrDefault("inputs", List.of())).size());

                switch (section) {
                    case "education" -> imported += parseEducations(sData, resume);
                    case "career" -> imported += parseCareers(sData, resume);
                    case "skills" -> imported += parseSkills(sData, resume);
                }
            }

            log.info("[LinkareerImporter] 가져오기 완료 - {}개 항목", imported);
            return SaraminResumeImporter.ImportResult.success(imported);

        } catch (Exception e) {
            log.error("[LinkareerImporter] 가져오기 실패: {}", e.getMessage(), e);
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
                basic.getOrDefault("gender", ""),
                basic.getOrDefault("birthDate", ""),
                basic.getOrDefault("address", ""));
        return 1;
    }

    @SuppressWarnings("unchecked")
    private int parseEducations(Map<String, Object> data, Resume resume) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        if (items == null) return 0;
        resume.getEducations().clear();
        int count = 0;
        for (Map<String, Object> item : items) {
            String name = str(item, "title");
            if (name.isEmpty()) continue;
            resume.getEducations().add(ResumeEducation.builder()
                    .resume(resume).schoolName(name).major(str(item, "subtitle"))
                    .schoolType(SchoolType.COLLEGE_4Y)
                    .startDate(str(item, "startDate")).endDate(str(item, "endDate"))
                    .sortOrder(count).build());
            count++;
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int parseCareers(Map<String, Object> data, Resume resume) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        if (items == null) return 0;
        resume.getCareers().clear();
        int count = 0;
        for (Map<String, Object> item : items) {
            String company = str(item, "title");
            if (company.isEmpty()) continue;
            resume.getCareers().add(ResumeCareer.builder()
                    .resume(resume).companyName(company)
                    .position(str(item, "subtitle"))
                    .jobDescription(str(item, "description"))
                    .startDate(str(item, "startDate")).endDate(str(item, "endDate"))
                    .sortOrder(count).build());
            count++;
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int parseSkills(Map<String, Object> data, Resume resume) {
        List<String> skills = (List<String>) data.get("skills");
        if (skills == null || skills.isEmpty()) return 0;
        // 기존 스킬에 추가 (다른 섹션에서 이미 추가했을 수 있음)
        int count = resume.getSkills().size();
        for (String skill : skills) {
            if (skill == null || skill.isBlank()) continue;
            // 중복 방지
            boolean exists = resume.getSkills().stream()
                    .anyMatch(s -> s.getSkillName().equalsIgnoreCase(skill.trim()));
            if (!exists) {
                resume.getSkills().add(ResumeSkill.builder()
                        .resume(resume).skillName(skill.trim())
                        .sortOrder(count).build());
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int parseDesiredConditions(Map<String, Object> data, Resume resume) {
        List<String> duties = (List<String>) data.get("duties");
        List<String> regions = (List<String>) data.get("regions");
        int count = 0;

        // 세부직무를 스킬로 저장
        if (duties != null && !duties.isEmpty()) {
            resume.getSkills().clear();
            for (String duty : duties) {
                if (duty != null && !duty.isBlank()) {
                    resume.getSkills().add(ResumeSkill.builder()
                            .resume(resume).skillName(duty.trim())
                            .sortOrder(count).build());
                    count++;
                }
            }
            log.info("[LinkareerImporter] 세부직무 {}개 추출", duties.size());
        }

        // 희망근무지역
        if (regions != null && !regions.isEmpty()) {
            String location = String.join(", ", regions);
            resume.updateDesiredConditions(null, null, location);
            count++;
            log.info("[LinkareerImporter] 희망근무지역: {}", location);
        }

        return count;
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString().trim() : "";
    }

    /** 링커리어 희망근무조건 섹션에서 세부직무/근무지역 추출 (CareerChip 컴포넌트) */
    private static final String PARSE_WORKING_CONDITION_SCRIPT = """
            () => {
              const result = { duties: [], regions: [] };
              const main = document.querySelector('main');
              if (!main) return result;

              // FieldChipList 안의 CareerChip에서 텍스트 추출
              const chipLists = main.querySelectorAll('.FieldChipList__StyledWrapper-sc-e5e6bec2-0');
              chipLists.forEach((chipList, index) => {
                chipList.querySelectorAll('.CareerChip__StyledWrapper-sc-8e5970a6-0 p').forEach(p => {
                  const text = p.textContent.trim();
                  if (!text) return;
                  if (index === 0) result.duties.push(text);    // 첫 번째 ChipList = 세부직무
                  else if (index === 1) result.regions.push(text); // 세 번째 = 근무지역 (두 번째는 업종)
                });
              });

              // 클래스명이 다를 수 있으니 fallback: 모든 칩에서 추출
              if (result.duties.length === 0 && result.regions.length === 0) {
                const allChips = main.querySelectorAll('[class*=Chip] p, [class*=chip] p');
                allChips.forEach(p => {
                  const text = p.textContent.trim();
                  if (text.includes('>')) result.regions.push(text); // "서울 > 전체" 패턴
                  else if (text.length > 2) result.duties.push(text);
                });
              }

              return result;
            }
            """;

    /** 링커리어 기본정보 섹션에서 input 필드 값 추출 */
    private static final String PARSE_INFO_SCRIPT = """
            () => {
              const result = { basic: {} };
              const inputs = document.querySelectorAll('input[type="text"], input[type="email"], input[type="tel"]');
              inputs.forEach(el => {
                const val = el.value || '';
                if (!val) return;
                const ph = (el.placeholder || '').toLowerCase();
                const name = (el.name || '').toLowerCase();
                const label = ph + ' ' + name;
                if (label.includes('이름') || label.includes('name')) result.basic.name = val;
                if (label.includes('이메일') || label.includes('email')) result.basic.email = val;
                if (label.includes('휴대폰') || label.includes('phone') || label.includes('숫자')) result.basic.phone = val;
                if (label.includes('생년월일') || label.includes('birth')) result.basic.birthDate = val;
                if (label.includes('주소') || label.includes('addr')) result.basic.address = val;
              });
              // 성별
              const genderSelected = document.querySelector('[class*=selected], [aria-selected=true]');
              if (genderSelected) {
                const text = genderSelected.textContent.trim();
                if (text === '남성' || text === '여성') result.basic.gender = text === '남성' ? '남' : '여';
              }
              return result;
            }
            """;

    /**
     * 링커리어 각 섹션에서 main 태그 안의 실제 컨텐츠만 추출.
     * footer, nav, header를 제외하고 main 안의 input/textarea/카드에서 데이터를 가져온다.
     */
    private static final String PARSE_MAIN_CONTENT_SCRIPT = """
            () => {
              const result = { section: '', items: [], inputs: [], skills: [] };

              // main 태그 안에서만 검색 (footer/nav 제외)
              const main = document.querySelector('main');
              if (!main) return result;

              // 섹션 이름 추출
              const heading = main.querySelector('h1, h2');
              result.section = heading ? heading.textContent.trim() : '';

              // 1. input/textarea 값 수집
              main.querySelectorAll('input[type="text"], input[type="email"], textarea').forEach(el => {
                const val = (el.value || '').trim();
                const placeholder = el.placeholder || '';
                if (val) {
                  result.inputs.push({ value: val, placeholder: placeholder });
                }
              });

              // 2. 입력된 데이터가 있는 fieldset/group 찾기
              const groups = main.querySelectorAll('fieldset, [role="group"], form > div > div');
              groups.forEach(group => {
                const inputs = group.querySelectorAll('input[type="text"], textarea, select');
                const values = {};
                inputs.forEach(input => {
                  const val = (input.value || '').trim();
                  const label = input.placeholder || input.name || '';
                  if (val) values[label] = val;
                });
                if (Object.keys(values).length >= 2) {
                  const vals = Object.values(values);
                  result.items.push({
                    title: vals[0] || '',
                    subtitle: vals[1] || '',
                    description: vals.slice(2).join(' '),
                    startDate: '',
                    endDate: ''
                  });
                }
              });

              // 3. 이미 등록된 항목 카드 추출 (수정/삭제 버튼이 있는 카드)
              main.querySelectorAll('button').forEach(btn => {
                const text = btn.textContent.trim();
                if (text === '수정' || text === '삭제') {
                  const card = btn.closest('div');
                  if (!card) return;
                  // 카드의 상위 컨테이너에서 텍스트 추출
                  const container = card.parentElement;
                  if (!container) return;
                  const cardText = container.innerText.split('\\n')
                    .map(t => t.trim())
                    .filter(t => t.length > 1 && t !== '수정' && t !== '삭제' && t !== '편집');
                  if (cardText.length >= 1) {
                    const datePattern = /\\d{4}[.\\-\\/]\\d{2}/;
                    const dates = cardText.filter(t => datePattern.test(t));
                    const nonDates = cardText.filter(t => !datePattern.test(t) && t.length > 1);
                    result.items.push({
                      title: nonDates[0] || '',
                      subtitle: nonDates[1] || '',
                      description: nonDates.slice(2).join(' '),
                      startDate: dates[0] || '',
                      endDate: dates[1] || ''
                    });
                  }
                }
              });

              // 4. 스킬/태그 추출 (main 안에서만)
              main.querySelectorAll('[class*=tag], [class*=badge], [class*=chip]').forEach(el => {
                const text = el.textContent.trim();
                if (text.length > 1 && text.length < 30
                    && !text.includes('추가') && !text.includes('삭제')
                    && !text.includes('저장') && !text.includes('편집')) {
                  result.skills.push(text);
                }
              });

              return result;
            }
            """;
}
