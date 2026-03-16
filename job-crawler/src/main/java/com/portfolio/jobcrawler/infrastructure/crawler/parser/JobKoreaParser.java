package com.portfolio.jobcrawler.infrastructure.crawler.parser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.portfolio.jobcrawler.infrastructure.crawler.dto.CrawledJobData;
import com.portfolio.jobcrawler.infrastructure.crawler.parser.category.JobKoreaJobCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 잡코리아 DOM 파싱 전담 클래스.
 * - 리스트: tr.devloopArea 기반 테이블
 * - 상세: Next.js 기반 (CSS 모듈 해시 클래스)
 * - 두 소스: 일반채용(/recruit/joblist) + 신입인턴(/theme/entry-level-internship)
 */
@Slf4j
@Component
public class JobKoreaParser implements SiteParser {

    private static final String JOBKOREA_BASE_URL = "https://www.jobkorea.co.kr";
    // IT 직무 코드 (duty 필터)
    private static final String IT_DUTY_URL = JOBKOREA_BASE_URL
            + "/recruit/joblist?menucode=duty&duession=2&orderby=RegDt";

    @Override
    public String getSiteName() {
        return "JOBKOREA";
    }

    @Override
    public String buildSearchUrl(String keyword, String jobCategory) {
        StringBuilder urlBuilder = new StringBuilder(IT_DUTY_URL);
        if (keyword != null && !keyword.isBlank()) {
            urlBuilder.append("&stext=").append(keyword);
        }
        return urlBuilder.toString();
    }

    @Override
    public Locator getListItems(Page page) {
        return page.locator("tr.devloopArea");
    }

    @Override
    public void waitForListLoaded(Page page) {
        try {
            page.waitForSelector(".titBx strong a.link",
                    new Page.WaitForSelectorOptions().setTimeout(15000));
        } catch (Exception e) {
            log.warn("[잡코리아-Parser] 공고 목록 로딩 지연: {}", e.getMessage());
        }
    }

    @Override
    public boolean goToNextPage(Page page, int currentPageNum) {
        int nextPage = currentPageNum + 1;

        // 다음 페이지 번호 클릭
        Locator pageLink = page.locator(".tplPagination a[data-page='" + nextPage + "']");
        if (pageLink.count() > 0) {
            log.info("[잡코리아-Parser] {}페이지로 이동", nextPage);
            pageLink.first().click();
            try { page.waitForTimeout(2000); } catch (Exception ignored) {}
            waitForListLoaded(page);
            return getListItems(page).count() > 0;
        }

        // 다음 범위 버튼
        Locator nextBtn = page.locator(".tplPagination a.btnPgnNext");
        if (nextBtn.count() > 0) {
            log.info("[잡코리아-Parser] '다음' 버튼 클릭");
            nextBtn.first().click();
            try { page.waitForTimeout(2000); } catch (Exception ignored) {}
            waitForListLoaded(page);
            return getListItems(page).count() > 0;
        }

        log.info("[잡코리아-Parser] 더 이상 페이지 없음");
        return false;
    }

    @Override
    public boolean supportsTwoPhase() {
        return true;
    }

    @Override
    public String extractDetailUrl(Page listPage, Locator item) {
        String href = safeAttr(item, ".titBx strong a.link", "href");
        if (href != null && href.contains("/Recruit/GI_Read/")) {
            return JOBKOREA_BASE_URL + href.split("\\?")[0];
        }
        return null;
    }

    @Override
    public CrawledJobData parseListData(Page listPage, Locator item, String requestedJobCategory) {
        String title = safeText(item, ".titBx strong a.link");
        if (title.isEmpty()) return null;

        String detailUrl = extractDetailUrl(listPage, item);
        if (detailUrl == null) return null;

        Locator cells = item.locator(".titBx .etc .cell");
        String career = cells.count() > 0 ? safeText(cells.nth(0)) : "";
        String education = cells.count() > 1 ? safeText(cells.nth(1)) : "";
        String location = cells.count() > 2 ? safeText(cells.nth(2)) : "";
        String employType = cells.count() > 3 ? safeText(cells.nth(3)) : "";
        String salary = cells.count() > 4 ? safeText(cells.nth(4)) : "";

        String company = safeText(item, "td.tplCo a.link");
        if (company.isEmpty()) company = safeText(item, ".coName");

        String techTags = safeText(item, ".titBx .dsc");
        String finalCategory = (requestedJobCategory != null && !requestedJobCategory.isBlank())
                ? requestedJobCategory : JobKoreaJobCategory.normalizeCategory(techTags);

        String careerFull = career;
        if (!employType.isEmpty()) careerFull += " · " + employType;

        return CrawledJobData.builder()
                .title(title).company(company).companyLogoUrl("")
                .location(location).url(detailUrl).sourceSite(getSiteName())
                .applicationMethod("HOMEPAGE").education(education).career(careerFull)
                .salary(salary).deadline("").jobCategory(finalCategory)
                .techStack(techTags.length() > 200 ? techTags.substring(0, 200) : techTags)
                .description("").requirements("").companyImages("")
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void enrichFromDetailPage(Page detailPage, CrawledJobData data) {
        try {
            try {
                detailPage.waitForSelector("iframe[src*='GI_Read_Comt_Ifrm'], h2",
                        new Page.WaitForSelectorOptions().setTimeout(8_000));
            } catch (Exception ignored) {}

            // 메인 페이지에서 구조화된 정보 추출
            Map<String, Object> detail = (Map<String, Object>) detailPage.evaluate("""
                (() => {
                    const result = {};
                    result.company = document.querySelector('h2')?.innerText?.trim() || '';
                    const aside = document.querySelector('aside');
                    if (aside) {
                        const lines = aside.innerText.split('\\n').map(l => l.trim()).filter(l => l);
                        for (let i = 0; i < lines.length - 1; i++) {
                            if (lines[i] === '경력') result.career = lines[i+1];
                            if (lines[i] === '급여') result.salary = lines[i+1];
                            if (lines[i] === '근무지역') result.location = lines[i+1];
                            if (lines[i] === '마감일') result.deadline = lines[i+1];
                            if (lines[i] === '고용형태') result.employType = lines[i+1];
                        }
                    }
                    const keepComponents = ['ApplyBox', 'CorpInformation', 'BenefitCard'];
                    const removeTexts = ['기업정보 더보기', '복리후생 더보기', '지도보기', '스크랩', '즉시 지원', '홈페이지 지원'];
                    let structuredDesc = '';
                    document.querySelectorAll('[data-sentry-component]').forEach(el => {
                        const comp = el.getAttribute('data-sentry-component');
                        if (keepComponents.some(k => comp?.includes(k))) {
                            let text = el.innerText.trim();
                            removeTexts.forEach(rt => { text = text.replaceAll(rt, ''); });
                            structuredDesc += text.trim() + '\\n\\n';
                        }
                    });
                    result.structuredDesc = structuredDesc.substring(0, 2000);
                    const bodyText = document.body.innerText || '';
                    if (bodyText.includes('잡코리아 즉시지원')) result.applyMethod = 'HOMEPAGE';
                    else if (bodyText.includes('홈페이지')) result.applyMethod = 'HOMEPAGE';
                    else result.applyMethod = 'UNKNOWN';
                    return result;
                })()
            """);

            // iframe 콘텐츠 추출
            String iframeContent = "";
            String companyImages = "";
            try {
                String iframeSrc = (String) detailPage.evaluate(
                        "document.querySelector('iframe[src*=\"GI_Read_Comt_Ifrm\"]')?.src || ''");
                if (iframeSrc != null && !iframeSrc.isEmpty()) {
                    Page iframePage = detailPage.context().newPage();
                    try {
                        iframePage.setDefaultNavigationTimeout(20_000);
                        iframePage.navigate(iframeSrc, new Page.NavigateOptions()
                                .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.LOAD));
                        Map<String, Object> iframeData = (Map<String, Object>) iframePage.evaluate("""
                            (() => {
                                const result = { text: '', images: [] };
                                const detail = document.querySelector('.secDetailWrap');
                                if (!detail) return result;

                                result.text = detail.innerText.trim();
                                result.images = Array.from(detail.querySelectorAll('img'))
                                    .filter(img => img.width > 300 && img.height > 200)
                                    .map(img => img.src)
                                    .filter(s => s && s.startsWith('http'))
                                    .filter((v, i, a) => a.indexOf(v) === i)
                                    .slice(0, 10);
                                return result;
                            })()
                        """);
                        if (iframeData != null) {
                            iframeContent = ((String) iframeData.getOrDefault("text", "")).trim();
                            // HTML 들여쓰기 공백 정리: 각 줄 앞뒤 공백 제거 후 빈 줄 압축
                            iframeContent = iframeContent.lines()
                                    .map(String::strip)
                                    .filter(line -> !line.isEmpty())
                                    .reduce((a, b) -> a + "\n" + b)
                                    .orElse("");
                            iframeContent = iframeContent.replaceAll("\\n{3,}", "\n\n");
                            if (iframeContent.length() > 5000) iframeContent = iframeContent.substring(0, 5000);
                            List<String> iframeImages = (List<String>) iframeData.getOrDefault("images", List.of());
                            if (!iframeImages.isEmpty()) {
                                companyImages = String.join(",", iframeImages);
                            }
                        }
                    } finally {
                        iframePage.close();
                    }
                }
            } catch (Exception e) {
                log.warn("[잡코리아-Parser] iframe 콘텐츠 추출 실패: {}", e.getMessage());
            }

            // 결과 조합하여 data에 세팅
            if (detail != null) {
                String structuredDesc = (String) detail.getOrDefault("structuredDesc", "");
                String description = iframeContent.isEmpty() ? structuredDesc : iframeContent + "\n\n" + structuredDesc;
                description = description.trim().replaceAll("\\n{3,}", "\n\n");
                if (description.length() > 5000) description = description.substring(0, 5000) + "...";
                data.setDescription(description);

                String detailCompany = (String) detail.getOrDefault("company", "");
                if (!detailCompany.isEmpty() && !detailCompany.equals("검색") && detailCompany.length() > 1) {
                    data.setCompany(detailCompany);
                }

                String dCareer = (String) detail.getOrDefault("career", "");
                if (!dCareer.isEmpty()) {
                    String dEmployType = (String) detail.getOrDefault("employType", "");
                    data.setCareer(dCareer + (!dEmployType.isEmpty() ? " · " + dEmployType : ""));
                }
                String dSalary = (String) detail.getOrDefault("salary", "");
                if (!dSalary.isEmpty()) data.setSalary(dSalary);
                String dLocation = (String) detail.getOrDefault("location", "");
                if (!dLocation.isEmpty()) data.setLocation(dLocation.replace(">", "").replace("&gt;", "").trim());

                data.setApplicationMethod((String) detail.getOrDefault("applyMethod", "HOMEPAGE"));
            }
            data.setCompanyImages(companyImages);

            log.info("[잡코리아-Parser] 수집: {} - {}", data.getCompany(), data.getTitle());
        } catch (Exception e) {
            log.warn("[잡코리아-Parser] 상세 페이지 보강 실패: {}", e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public CrawledJobData parseJobData(Page listPage, Locator item, String requestedJobCategory) {
        CrawledJobData data = parseListData(listPage, item, requestedJobCategory);
        if (data == null) return null;

        Page detailPage = listPage.context().newPage();
        try {
            detailPage.setDefaultNavigationTimeout(15_000);
            detailPage.navigate(data.getUrl(), new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.LOAD));
            enrichFromDetailPage(detailPage, data);
        } catch (Exception e) {
            log.warn("[잡코리아-Parser] 상세 페이지 실패 ({}): {}", data.getUrl(), e.getMessage());
        } finally {
            detailPage.close();
        }
        return data;
    }

    private String safeText(Locator parent, String selector) {
        try {
            Locator loc = parent.locator(selector);
            if (loc.count() > 0) return loc.first().innerText().trim();
        } catch (Exception ignored) {}
        return "";
    }

    private String safeText(Locator locator) {
        try { return locator.innerText().trim(); } catch (Exception ignored) {}
        return "";
    }

    private String safeAttr(Locator parent, String selector, String attr) {
        try {
            Locator loc = parent.locator(selector);
            if (loc.count() > 0) return loc.first().getAttribute(attr);
        } catch (Exception ignored) {}
        return null;
    }
}
