package com.portfolio.jobcrawler.infrastructure.crawler.parser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.portfolio.jobcrawler.infrastructure.crawler.dto.CrawledJobData;
import com.portfolio.jobcrawler.global.util.HtmlSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import com.portfolio.jobcrawler.infrastructure.crawler.parser.category.JobPlanetJobCategory;

/**
 * 잡플래닛 DOM 파싱 전담 클래스.
 * 검색 목록에서 공고 클릭 → 오른쪽 상세 패널에서 데이터 추출.
 */
@Slf4j
@Component
public class JobPlanetParser implements SiteParser {

    private static final String JOBPLANET_BASE_URL = "https://www.jobplanet.co.kr";

    @Override
    public String getSiteName() {
        return "JOBPLANET";
    }

    @Override
    public String buildSearchUrl(String keyword, String jobCategory) {
        StringBuilder urlBuilder = new StringBuilder(JOBPLANET_BASE_URL);
        urlBuilder.append("/search/job?");

        boolean hasQuery = false;
        if (keyword != null && !keyword.isBlank()) {
            try {
                urlBuilder.append("query=").append(URLEncoder.encode(keyword, StandardCharsets.UTF_8.toString()));
                hasQuery = true;
            } catch (Exception e) {}
        }

        if (jobCategory != null && !jobCategory.isBlank() && !jobCategory.equals("전체") && !jobCategory.equals("all")) {
            String catId = JobPlanetJobCategory.getCodeByDisplayName(jobCategory);
            if (catId != null) {
                if (hasQuery) urlBuilder.append("&");
                urlBuilder.append("category_ids%5B%5D=").append(catId);
                hasQuery = true;
            } else {
                try {
                    String encodedCat = URLEncoder.encode(jobCategory, StandardCharsets.UTF_8.toString());
                    if (hasQuery) urlBuilder.append("+").append(encodedCat);
                    else urlBuilder.append("query=").append(encodedCat);
                    hasQuery = true;
                } catch (Exception e) {}
            }
        }

        if (hasQuery) {
            urlBuilder.append("&order_by=recent");
        } else {
            urlBuilder.append("order_by=recent");
        }

        return urlBuilder.toString();
    }

    @Override
    public Locator getListItems(Page page) {
        Locator items = page.locator("a.group.z-0.block");
        if (items.count() == 0) {
            items = page.locator("a[href*='posting_ids']");
        }
        return items;
    }

    @Override
    public void waitForListLoaded(Page page) {
        try {
            page.waitForSelector("a.group.z-0.block, a[href*='posting_ids']",
                    new Page.WaitForSelectorOptions().setTimeout(15000));
        } catch (Exception e) {
            log.warn("[잡플래닛-Parser] 공고 목록 로딩 지연 또는 없음: {}", e.getMessage());
        }
    }

    @Override
    public boolean goToNextPage(Page page, int currentPageNum) {
        Locator nextBtn = page.locator("button:has-text('다음'), a:has-text('다음'), [aria-label='Next']");
        if (nextBtn.count() > 0) {
            nextBtn.first().click();
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            return true;
        }
        return false;
    }

    @Override
    public boolean supportsTwoPhase() {
        return true;
    }

    @Override
    public String extractDetailUrl(Page listPage, Locator item) {
        return extractJobUrl(item);
    }

    @Override
    public CrawledJobData parseListData(Page listPage, Locator item, String requestedJobCategory) {
        String title = safeText(item, "h4");
        String company = safeText(item, "em");
        String companyLogoUrl = extractCompanyLogo(item);

        if (title.isEmpty() || company.isEmpty()) {
            return null;
        }

        String jobUrl = extractJobUrl(item);
        List<String> techList = new ArrayList<>();
        String career = extractCareerAndTechStack(item, techList);

        String finalCategory;
        if (requestedJobCategory != null && !requestedJobCategory.isBlank()
                && !requestedJobCategory.equals("전체") && !requestedJobCategory.equals("all")) {
            finalCategory = requestedJobCategory;
        } else {
            finalCategory = "기타";
        }

        return CrawledJobData.builder()
                .title(title).company(company).companyLogoUrl(companyLogoUrl)
                .location("").url(jobUrl).sourceSite(getSiteName())
                .applicationMethod("HOMEPAGE").education("").career(career)
                .salary("").jobCategory(finalCategory).deadline("")
                .techStack(String.join(",", techList))
                .description("").requirements("").companyImages("")
                .build();
    }

    @Override
    public void enrichFromDetailPage(Page detailPage, CrawledJobData data) {
        try {
            // 상세 패널이 로드될 때까지 대기
            try {
                detailPage.waitForSelector("h3:has-text('주요 업무'), h3:has-text('자격 요건'), h3:has-text('요약')",
                        new Page.WaitForSelectorOptions().setTimeout(8000));
                detailPage.waitForTimeout(1000);
            } catch (Exception e) {
                log.debug("[잡플래닛-Parser] 상세 패널 로딩 대기: {}", e.getMessage());
            }

            // JS로 상세 패널 데이터 추출
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) detailPage.evaluate(DETAIL_PARSE_SCRIPT);

            if (result == null || result.isEmpty()) {
                log.warn("[잡플래닛-Parser] 상세 데이터 추출 실패");
                return;
            }

            // 요약 정보 세팅
            String deadline = str(result, "마감일");
            if (!deadline.isEmpty()) data.setDeadline(deadline);

            String career = str(result, "경력");
            if (!career.isEmpty()) data.setCareer(career);

            String location = str(result, "근무지역");
            if (!location.isEmpty()) data.setLocation(location);

            String skill = str(result, "스킬");
            if (!skill.isEmpty()) data.setTechStack(skill);

            String jobType = str(result, "직무");
            if (!jobType.isEmpty()) data.setJobCategory(jobType);

            String employType = str(result, "고용형태");

            // description 조립 (HTML)
            StringBuilder desc = new StringBuilder();
            desc.append("<h3>채용 요약</h3><ul>");
            if (!deadline.isEmpty()) desc.append("<li><strong>마감일</strong>: ").append(deadline).append("</li>");
            if (!jobType.isEmpty()) desc.append("<li><strong>직무</strong>: ").append(jobType).append("</li>");
            if (!career.isEmpty()) desc.append("<li><strong>경력</strong>: ").append(career).append("</li>");
            if (!employType.isEmpty()) desc.append("<li><strong>고용형태</strong>: ").append(employType).append("</li>");
            if (!location.isEmpty()) desc.append("<li><strong>근무지역</strong>: ").append(location).append("</li>");
            if (!skill.isEmpty()) desc.append("<li><strong>스킬</strong>: ").append(skill).append("</li>");
            desc.append("</ul>");

            // 각 섹션 추가
            String[] sectionOrder = {"기업 소개", "주요 업무", "자격 요건", "우대사항", "채용 절차", "복지 및 혜택", "회사위치", "담당자 연락처"};
            @SuppressWarnings("unchecked")
            Map<String, String> sections = (Map<String, String>) result.get("sections");

            StringBuilder reqBuilder = new StringBuilder();

            if (sections != null) {
                for (String sectionName : sectionOrder) {
                    String content = sections.get(sectionName);
                    if (content != null && !content.isBlank()) {
                        desc.append("<h3>").append(sectionName).append("</h3>");
                        // 줄바꿈을 <br>로, • 불릿을 리스트로 변환
                        String htmlContent = convertTextToHtml(content);
                        desc.append(htmlContent);

                        if (sectionName.equals("자격 요건")) {
                            reqBuilder.append(content);
                        }
                    }
                }
            }

            // 기업 이미지
            @SuppressWarnings("unchecked")
            List<String> images = (List<String>) result.get("images");
            if (images != null && !images.isEmpty()) {
                data.setCompanyImages(String.join(",", images.stream().limit(10).toList()));
            }

            String description = desc.toString();
            if (description.length() > 10000) {
                description = description.substring(0, 10000);
            }
            data.setDescription(description);
            data.setRequirements(reqBuilder.toString().trim());

        } catch (Exception e) {
            log.warn("[잡플래닛-Parser] 상세 페이지 보강 실패: {}", e.getMessage());
        }
    }

    @Override
    public CrawledJobData parseJobData(Page listPage, Locator item, String requestedJobCategory) {
        CrawledJobData data = parseListData(listPage, item, requestedJobCategory);
        if (data == null || data.getUrl() == null || data.getUrl().isBlank()) return data;

        Page detailPage = listPage.context().newPage();
        try {
            detailPage.setDefaultNavigationTimeout(60_000);
            detailPage.navigate(data.getUrl(), new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));

            // 공고 클릭하여 상세 패널 열기
            Locator firstItem = detailPage.locator("a.group.z-0.block, a[href*='posting_ids']").first();
            if (firstItem.count() > 0) {
                firstItem.click();
                detailPage.waitForTimeout(2000);
            }

            enrichFromDetailPage(detailPage, data);
        } catch (Exception e) {
            log.warn("[잡플래닛-Parser] 상세 페이지 크롤링 실패 ({}): {}", data.getUrl(), e.getMessage());
        } finally {
            detailPage.close();
        }
        return data;
    }

    /**
     * 텍스트를 HTML로 변환 (줄바꿈 → br, • 불릿 → ul/li)
     */
    private String convertTextToHtml(String text) {
        if (text == null || text.isBlank()) return "";
        String[] lines = text.split("\n");
        StringBuilder html = new StringBuilder();
        boolean inList = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            boolean isBullet = trimmed.startsWith("•") || trimmed.startsWith("·")
                    || trimmed.startsWith("ㆍ") || trimmed.startsWith("-");

            if (isBullet) {
                if (!inList) {
                    html.append("<ul>");
                    inList = true;
                }
                String content = trimmed.substring(1).trim();
                html.append("<li>").append(content).append("</li>");
            } else {
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
                html.append("<p>").append(trimmed).append("</p>");
            }
        }
        if (inList) html.append("</ul>");
        return html.toString();
    }

    private String extractJobUrl(Locator item) {
        String jobUrl = item.getAttribute("href");
        if (jobUrl != null && !jobUrl.startsWith("http")) {
            return JOBPLANET_BASE_URL + jobUrl;
        }
        return jobUrl;
    }

    private String extractCompanyLogo(Locator item) {
        String logoUrl = safeAttr(item, ".company_logo img, .logo img", "src");
        if (logoUrl == null || logoUrl.isEmpty()) {
            logoUrl = safeAttr(item, "img[alt*='로고'], img", "src");
        }
        return logoUrl != null ? logoUrl : "";
    }

    private String extractCareerAndTechStack(Locator item, List<String> techList) {
        Locator spans = item.locator("span");
        String career = "";

        for (int i = 0; i < spans.count(); i++) {
            String text = safeText(spans.nth(i));
            if (text.isEmpty()) continue;
            if (text.matches(".*\\d+년.*") || text.matches(".*\\d+\\s*~.*")) {
                career = text;
            } else {
                techList.add(text);
            }
        }
        return career;
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString().trim() : "";
    }

    /**
     * 잡플래닛 상세 패널에서 데이터를 추출하는 JS 스크립트.
     * h3 헤딩 기준으로 섹션을 분리하여 제목-내용 매핑.
     */
    private static final String DETAIL_PARSE_SCRIPT = """
            () => {
              const result = { sections: {} };

              // 요약 정보 (dl/dt/dd)
              const dls = document.querySelectorAll('dl');
              dls.forEach(dl => {
                const dt = dl.querySelector('dt');
                const dd = dl.querySelector('dd');
                if (dt && dd) {
                  const key = dt.textContent.trim();
                  const val = dd.textContent.trim();
                  if (key && val) result[key] = val;
                }
              });

              // 섹션별 내용 (h3 다음 형제 요소에서 텍스트 추출)
              const headings = document.querySelectorAll('h3');
              const sectionNames = ['기업 소개', '주요 업무', '자격 요건', '우대사항', '채용 절차',
                                    '복지 및 혜택', '회사위치', '담당자 연락처', '기업 스토리'];

              headings.forEach(h3 => {
                const title = h3.textContent.trim();
                if (!sectionNames.includes(title)) return;

                // h3의 다음 형제에서 내용 수집
                let content = '';
                let next = h3.nextElementSibling;
                while (next && next.tagName !== 'H3') {
                  const text = next.textContent.trim();
                  if (text) content += text + '\\n';
                  next = next.nextElementSibling;
                }

                // 부모 컨테이너에서도 시도
                if (!content) {
                  const parent = h3.closest('div');
                  if (parent) {
                    const p = parent.querySelector('p');
                    if (p) content = p.textContent.trim();
                  }
                }

                if (content) result.sections[title] = content.trim();
              });

              // 기업 이미지
              result.images = [];
              document.querySelectorAll('img[alt="기업 이미지"]').forEach(img => {
                const src = img.src || img.getAttribute('data-src');
                if (src && src.startsWith('http')
                    && !src.includes('blank')
                    && !src.includes('transparent')
                    && !src.includes('placeholder')
                    && !src.includes('bg_transparent')) {
                  result.images.push(src);
                }
              });

              return result;
            }
            """;

    private String safeText(Locator parent, String selector) {
        try {
            Locator loc = parent.locator(selector);
            if (loc.count() > 0) {
                String text = loc.first().innerText();
                return text != null ? text.trim() : "";
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String safeAttr(Locator parent, String selector, String attr) {
        try {
            Locator loc = parent.locator(selector);
            if (loc.count() > 0) {
                return loc.first().getAttribute(attr);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String safeText(Locator locator) {
        try {
            String text = locator.innerText();
            return text != null ? text.trim() : "";
        } catch (Exception ignored) {}
        return "";
    }
}
