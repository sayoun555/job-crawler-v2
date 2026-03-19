package com.portfolio.jobcrawler.infrastructure.crawler.parser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.portfolio.jobcrawler.infrastructure.crawler.dto.CrawledJobData;
import com.portfolio.jobcrawler.infrastructure.crawler.parser.category.JobKoreaJobCategory;
import com.portfolio.jobcrawler.global.util.HtmlSanitizer;
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
                detailPage.waitForSelector("iframe#gib_frame, iframe[src*='GI_Read_Comt_Ifrm'], h2, h1",
                        new Page.WaitForSelectorOptions().setTimeout(8_000));
                // iframe src가 동적 로드될 수 있으므로 추가 대기
                detailPage.waitForTimeout(2000);
            } catch (Exception ignored) {}

            // 헤드헌팅 페이지 감지 (리다이렉트 후 URL 또는 DOM으로 판별)
            boolean isHeadhunting = detailPage.url().contains("PageGbn=HH")
                    || detailPage.locator("h1:has-text('헤드헌팅')").count() > 0
                    || detailPage.locator("text=헤드헌팅 채용정보 보기").count() > 0;

            if (isHeadhunting) {
                enrichFromHeadhuntingPage(detailPage, data);
                return;
            }

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
                    // 전체 DOM에서 마감일 검색 (정확한 날짜 형식 우선)
                    const allEls = document.querySelectorAll('*');
                    for (const el of allEls) {
                        if (el.textContent?.trim() === '마감일' && el.children.length === 0) {
                            const next = el.nextElementSibling || el.parentElement?.nextElementSibling;
                            if (next) { result.deadline = next.textContent.trim(); break; }
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
                String iframeSrc = (String) detailPage.evaluate("""
                    (() => {
                        const selectors = [
                            'iframe[src*="GI_Read_Comt_Ifrm"]',
                            'iframe#gib_frame',
                            'iframe[src*="Gno="]'
                        ];
                        for (const sel of selectors) {
                            const el = document.querySelector(sel);
                            if (el && el.src && el.src.startsWith('http')) return el.src;
                        }
                        return '';
                    })()
                """);
                if (iframeSrc != null && !iframeSrc.isBlank() && iframeSrc.startsWith("http")) {
                    Page iframePage = detailPage.context().newPage();
                    try {
                        iframePage.setDefaultNavigationTimeout(20_000);
                        iframePage.navigate(iframeSrc, new Page.NavigateOptions()
                                .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.LOAD));
                        Map<String, Object> iframeData = (Map<String, Object>) iframePage.evaluate("""
                            (() => {
                                const result = { html: '', images: [] };
                                // 새 구조: article 또는 .secDetailWrap
                                const detail = document.querySelector('.detailed-summary-contents')
                                             || document.querySelector('article')
                                             || document.querySelector('.secDetailWrap')
                                             || document.querySelector('body > div')
                                             || document.querySelector('body');
                                if (!detail) return result;

                                result.html = detail.innerHTML;
                                result.images = Array.from(detail.querySelectorAll('img'))
                                    .filter(img => img.width > 50 && img.height > 50)
                                    .map(img => img.src)
                                    .filter(s => s && s.startsWith('http')
                                        && !s.includes('blank') && !s.includes('transparent')
                                        && !s.includes('spacer') && !s.includes('pixel'))
                                    .filter((v, i, a) => a.indexOf(v) === i)
                                    .slice(0, 10);
                                return result;
                            })()
                        """);
                        if (iframeData != null) {
                            String rawHtml = ((String) iframeData.getOrDefault("html", "")).trim();
                            if (!rawHtml.isEmpty()) {
                                iframeContent = HtmlSanitizer.sanitize(rawHtml);
                                if (iframeContent.length() > 10000) iframeContent = iframeContent.substring(0, 10000);
                            }
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

            if (detail != null) {
                String structuredDesc = (String) detail.getOrDefault("structuredDesc", "");
                String description = iframeContent.isEmpty() ? structuredDesc : iframeContent + "\n\n" + structuredDesc;
                description = description.trim().replaceAll("\\n{3,}", "\n\n");

                String detailCompany = (String) detail.getOrDefault("company", "");
                String validCompany = (!detailCompany.isEmpty() && !detailCompany.equals("검색") && detailCompany.length() > 1)
                        ? detailCompany : null;

                String dCareer = (String) detail.getOrDefault("career", "");
                String dEmployType = (String) detail.getOrDefault("employType", "");
                String combinedCareer = !dCareer.isEmpty()
                        ? dCareer + (!dEmployType.isEmpty() ? " · " + dEmployType : "") : null;

                String dSalary = (String) detail.getOrDefault("salary", "");
                String dLocation = (String) detail.getOrDefault("location", "");
                String cleanLocation = !dLocation.isEmpty()
                        ? dLocation.replace(">", "").replace("&gt;", "").trim() : null;
                String dDeadline = (String) detail.getOrDefault("deadline", "");

                data.enrichBasicInfo(null, validCompany, cleanLocation);
                data.enrichJobDetail(description, null, companyImages);
                data.enrichConditions(combinedCareer, dSalary, dDeadline, null);
                data.enrichClassification(null, null, (String) detail.getOrDefault("applyMethod", "HOMEPAGE"));
            } else {
                data.enrichJobDetail(null, null, companyImages);
            }

            log.info("[잡코리아-Parser] 수집: {} - {}", data.getCompany(), data.getTitle());
        } catch (Exception e) {
            log.warn("[잡코리아-Parser] 상세 페이지 보강 실패: {}", e.getMessage());
        }
    }

    /**
     * 헤드헌팅 공고 전용 파싱. dl/dt/dd 구조에서 데이터를 추출한다.
     */
    @SuppressWarnings("unchecked")
    private void enrichFromHeadhuntingPage(Page detailPage, CrawledJobData data) {
        try {
            // iframe src 동적 로드 대기
            try {
                detailPage.waitForSelector("iframe#gib_frame[src*='GI_Read']",
                        new Page.WaitForSelectorOptions().setTimeout(5_000));
            } catch (Exception ignored) {
                // iframe이 없는 헤드헌팅 공고도 있음
                detailPage.waitForTimeout(2000);
            }

            Map<String, Object> detail = (Map<String, Object>) detailPage.evaluate("""
                (() => {
                    const result = {};
                    // dl/dt/dd에서 정보 추출
                    const dts = document.querySelectorAll('dt');
                    const dds = document.querySelectorAll('dd');
                    for (let i = 0; i < dts.length; i++) {
                        const key = dts[i]?.textContent?.trim() || '';
                        const dd = dts[i]?.nextElementSibling;
                        if (!dd) continue;
                        const val = dd.textContent?.trim() || '';
                        if (key === '채용 기업정보' || key.includes('기업정보')) {
                            // 다음 dd들에서 회사명, 기업구분 추출
                            let next = dts[i].nextElementSibling;
                            while (next && next.tagName === 'DD') {
                                const t = next.textContent.trim().replace('·','').trim();
                                if (t && !result.company) result.company = t;
                                else if (t) result.companyType = t;
                                next = next.nextElementSibling;
                            }
                        }
                        if (key === '근무조건') {
                            let items = [];
                            let next = dts[i].nextElementSibling;
                            while (next && next.tagName === 'DD') {
                                items.push(next.textContent.trim().replace('·','').trim());
                                next = next.nextElementSibling;
                            }
                            if (items.length > 0) result.salary = items[0];
                            if (items.length > 1) result.location = items[1];
                        }
                        if (key === '지원자격') {
                            let items = [];
                            let next = dts[i].nextElementSibling;
                            while (next && next.tagName === 'DD') {
                                items.push(next.textContent.trim().replace('·','').trim());
                                next = next.nextElementSibling;
                                if (next && next.tagName === 'DT') break;
                            }
                            result.career = items.join(', ');
                        }
                        if (key === '스킬') {
                            result.skills = val.replace('·','').trim();
                        }
                        if (key === '고용형태') {
                            let items = [];
                            let next = dts[i].nextElementSibling;
                            while (next && next.tagName === 'DD') {
                                items.push(next.textContent.trim().replace('·','').trim());
                                next = next.nextElementSibling;
                                if (next && next.tagName === 'DT') break;
                            }
                            result.employType = items.join(', ');
                        }
                    }
                    return result;
                })()
            """);

            if (detail != null) {
                String company = (String) detail.getOrDefault("company", "");
                String salary = (String) detail.getOrDefault("salary", "");
                String location = (String) detail.getOrDefault("location", "");
                String career = (String) detail.getOrDefault("career", "");
                String skills = (String) detail.getOrDefault("skills", "");
                String employType = (String) detail.getOrDefault("employType", "");

                data.enrichBasicInfo(null, company, location);
                data.enrichConditions(career, salary, null, null);
                data.enrichClassification(null, skills, null);

                // 채용 요약 HTML
                StringBuilder desc = new StringBuilder("<h3>채용 요약</h3><ul>");
                if (!company.isEmpty()) desc.append("<li><strong>기업</strong>: ").append(company).append("</li>");
                if (!employType.isEmpty()) desc.append("<li><strong>고용형태</strong>: ").append(employType).append("</li>");
                if (!career.isEmpty()) desc.append("<li><strong>지원자격</strong>: ").append(career).append("</li>");
                if (!salary.isEmpty()) desc.append("<li><strong>급여</strong>: ").append(salary).append("</li>");
                if (!location.isEmpty()) desc.append("<li><strong>근무지</strong>: ").append(location).append("</li>");
                if (!skills.isEmpty()) desc.append("<li><strong>스킬</strong>: ").append(skills).append("</li>");
                desc.append("</ul>");

                // iframe 본문 — contentDocument로 직접 접근 (새 페이지 열기 대신)
                @SuppressWarnings("unchecked")
                Map<String, Object> iframeData = (Map<String, Object>) detailPage.evaluate("""
                    (() => {
                        const result = { html: '', images: [] };
                        const selectors = ['iframe#gib_frame', 'iframe[src*="GI_Read_Comt_Ifrm"]', 'iframe[src*="Gno="]'];
                        let doc = null;
                        for (const sel of selectors) {
                            const iframe = document.querySelector(sel);
                            if (iframe) {
                                try { doc = iframe.contentDocument; } catch(e) {}
                                if (doc) break;
                            }
                        }
                        if (!doc) return result;
                        const detail = doc.querySelector('.detailed-summary-contents')
                                     || doc.querySelector('article')
                                     || doc.querySelector('.secDetailWrap')
                                     || doc.querySelector('body > div');
                        if (!detail) return result;
                        result.html = detail.innerHTML;
                        result.images = Array.from(detail.querySelectorAll('img'))
                            .filter(img => img.width > 50 && img.height > 50)
                            .map(img => img.src)
                            .filter(s => s && s.startsWith('http')
                                && !s.includes('blank') && !s.includes('transparent'))
                            .filter((v, i, a) => a.indexOf(v) === i)
                            .slice(0, 10);
                        return result;
                    })()
                """);
                String companyImages = null;
                if (iframeData != null) {
                    String rawHtml = ((String) iframeData.getOrDefault("html", "")).trim();
                    if (!rawHtml.isEmpty()) {
                        desc.append(HtmlSanitizer.sanitize(rawHtml));
                    }
                    @SuppressWarnings("unchecked")
                    List<String> imgList = (List<String>) iframeData.getOrDefault("images", List.of());
                    if (!imgList.isEmpty()) {
                        companyImages = String.join(",", imgList);
                    }
                }

                data.enrichJobDetail(desc.toString(), null, companyImages);
            }

            log.info("[잡코리아-Parser] 헤드헌팅 수집: {} - {}", data.getCompany(), data.getTitle());
        } catch (Exception e) {
            log.warn("[잡코리아-Parser] 헤드헌팅 파싱 실패: {}", e.getMessage());
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
