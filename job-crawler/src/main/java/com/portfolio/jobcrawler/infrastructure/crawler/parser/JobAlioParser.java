package com.portfolio.jobcrawler.infrastructure.crawler.parser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.portfolio.jobcrawler.infrastructure.crawler.dto.CrawledJobData;
import com.portfolio.jobcrawler.global.util.HtmlSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 잡알리오(공공기관 채용정보시스템) DOM 파싱 전담 클래스.
 * - URL: job.alio.go.kr/recruit.do
 * - SSR 기반 테이블 구조 (.tbl.type_03)
 * - 정보통신(전산직) NCS 코드: R600020
 */
@Slf4j
@Component
public class JobAlioParser implements SiteParser {

    private static final String JOBALIO_BASE_URL = "https://job.alio.go.kr";

    @Override
    public String getSiteName() {
        return "JOBALIO";
    }

    @Override
    public String buildSearchUrl(String keyword, String jobCategory, String companyType) {
        StringBuilder urlBuilder = new StringBuilder(JOBALIO_BASE_URL);
        urlBuilder.append("/recruit.do?");
        // 전산직(근무분야) 고정
        urlBuilder.append("area=R8018");
        // 등록일 범위: 2개월 전 ~ 충분히 먼 미래
        java.time.LocalDate now = java.time.LocalDate.now();
        String sDate = now.minusMonths(2).format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd"));
        urlBuilder.append("&s_date=").append(sDate);
        urlBuilder.append("&e_date=2045.12.31");
        // 진행중인 공고만
        urlBuilder.append("&ing=2");
        urlBuilder.append("&pageSet=50");
        urlBuilder.append("&order=REG_DATE&sort=DESC");

        if (keyword != null && !keyword.isBlank()) {
            urlBuilder.append("&search_type=TITLE&keyword=").append(keyword);
        }

        return urlBuilder.toString();
    }

    @Override
    public Locator getListItems(Page page) {
        return page.locator(".tbl.type_03 tbody tr");
    }

    @Override
    public void waitForListLoaded(Page page) {
        try {
            page.waitForSelector(".tbl.type_03 tbody tr td a",
                    new Page.WaitForSelectorOptions().setTimeout(15000));
        } catch (Exception e) {
            log.warn("[잡알리오-Parser] 공고 목록 로딩 지연: {}", e.getMessage());
        }
    }

    @Override
    public boolean goToNextPage(Page page, int currentPageNum) {
        int nextPage = currentPageNum + 1;

        // goPage(N) onclick을 가진 페이지 번호 링크 찾기
        Locator pageLinks = page.locator(".page a:not(.start):not(.prev):not(.next):not(.end):not(.current)");
        for (int i = 0; i < pageLinks.count(); i++) {
            String onclick = pageLinks.nth(i).getAttribute("onclick");
            if (onclick != null && onclick.contains("goPage(" + nextPage + ")")) {
                pageLinks.nth(i).click();
                waitForListLoaded(page);
                return getListItems(page).count() > 0;
            }
        }

        // 다음 범위 버튼 (.next)
        Locator nextRange = page.locator(".page a.next");
        if (nextRange.count() > 0) {
            String onclick = nextRange.first().getAttribute("onclick");
            if (onclick != null && !onclick.contains("return false;")) {
                nextRange.first().click();
                waitForListLoaded(page);
                return getListItems(page).count() > 0;
            }
        }

        return false;
    }

    @Override
    public boolean supportsTwoPhase() {
        return true;
    }

    @Override
    public String extractDetailUrl(Page listPage, Locator item) {
        String href = safeAttr(item, "td:nth-child(3) a", "href");
        if (href == null || href.isEmpty()) return null;
        if (href.startsWith("/")) return JOBALIO_BASE_URL + href;
        return href;
    }

    @Override
    public CrawledJobData parseListData(Page listPage, Locator item, String requestedJobCategory) {
        String title = safeText(item, "td:nth-child(3) a");
        if (title.isEmpty()) return null;

        String org = safeText(item, "td:nth-child(4)");
        String location = safeText(item, "td:nth-child(5)");
        String employType = safeText(item, "td:nth-child(6)");
        String regDate = safeText(item, "td:nth-child(7)");
        String deadline = safeText(item, "td:nth-child(8)").replaceAll("\\s+", " ").trim();
        String status = safeText(item, "td:nth-child(9)");

        // 마감된 공고 스킵
        if (status.contains("마감")) return null;

        // D-day 추출
        if (deadline.contains("D-")) {
            deadline = deadline.replaceAll(".*?(\\d{2}\\.\\d{2}\\.\\d{2}).*", "$1").trim();
        } else {
            deadline = deadline.replaceAll("[^0-9.]", "").trim();
        }
        if (deadline.matches("\\d{2}\\.\\d{2}\\.\\d{2}")) {
            deadline = "20" + deadline;
        }

        String url = extractDetailUrl(listPage, item);
        if (url == null) return null;

        // 경력 정보는 상세 페이지에서 보강
        return CrawledJobData.builder()
                .title(title).company(org)
                .location(location).url(url).sourceSite(getSiteName())
                .applicationMethod("HOMEPAGE")
                .education("").career(employType)
                .salary("").deadline(deadline)
                .techStack("").jobCategory("공기업")
                .description("").requirements("").companyImages("")
                .build();
    }

    @Override
    public CrawledJobData parseJobData(Page listPage, Locator item, String requestedJobCategory) {
        return parseListData(listPage, item, requestedJobCategory);
    }

    @Override
    public void enrichFromDetailPage(Page detailPage, CrawledJobData data) {
        try {
            detailPage.waitForSelector(".tbl, .tab-content",
                    new Page.WaitForSelectorOptions().setTimeout(10_000));

            StringBuilder desc = new StringBuilder();
            StringBuilder req = new StringBuilder();

            // 1. 상단 테이블: 채용 요약 정보
            Locator table = detailPage.locator("table").first();
            if (table.count() > 0) {
                Locator ths = table.locator("th");
                Locator tds = table.locator("td");

                desc.append("<h3>채용 요약</h3><ul>");
                for (int i = 0; i < Math.min(ths.count(), tds.count()); i++) {
                    String th = ths.nth(i).textContent().trim();
                    String td = tds.nth(i).textContent().trim();
                    if (td.isEmpty()) continue;

                    desc.append("<li><strong>").append(th).append("</strong>: ").append(td).append("</li>");
                    if (th.contains("학력")) data.enrichConditions(null, null, null, td);
                    if (th.contains("급여")) data.enrichConditions(null, td, null, null);
                    if (th.contains("채용구분")) data.enrichConditions(td, null, null, null);
                }
                desc.append("</ul>");
            }

            // 2. tab-1: 상세요강 (응시자격, 결격사유, 우대내용, 전형절차)
            Locator tab1 = detailPage.locator("#tab-1.tab-content");
            if (tab1.count() > 0) {
                String tab1Html = tab1.first().innerHTML();
                desc.append("<h3>상세요강</h3>").append(HtmlSanitizer.sanitize(tab1Html));

                // 응시자격/우대내용을 requirements로 추출
                Locator h4s = tab1.locator("h4");
                for (int i = 0; i < h4s.count(); i++) {
                    String heading = h4s.nth(i).textContent().trim();
                    if (heading.contains("응시자격") || heading.contains("우대") || heading.contains("전형절차")) {
                        Locator nextP = h4s.nth(i).locator("~ p").first();
                        if (nextP.count() > 0) {
                            req.append("[").append(heading).append("]\n");
                            req.append(nextP.textContent().trim()).append("\n\n");
                        }
                    }
                }
            }

            // 3. tab-2: 전형단계별 채용정보
            Locator tab2 = detailPage.locator("#tab-2.tab-content");
            if (tab2.count() > 0) {
                String tab2Html = tab2.first().innerHTML();
                String sanitized = HtmlSanitizer.sanitize(tab2Html);
                if (!sanitized.isBlank()) {
                    desc.append("<h3>전형단계별 채용정보</h3>").append(sanitized);
                }
            }

            // 4. tab-3: 첨부파일 전체보기
            Locator tab3 = detailPage.locator("#tab-3.tab-content");
            if (tab3.count() > 0) {
                Locator fileLinks = tab3.locator("a[href*='download']");
                if (fileLinks.count() > 0) {
                    desc.append("<h3>첨부파일</h3><ul>");
                    for (int i = 0; i < fileLinks.count(); i++) {
                        String linkText = fileLinks.nth(i).textContent().trim();
                        String href = fileLinks.nth(i).getAttribute("href");
                        if (!linkText.isEmpty() && href != null) {
                            desc.append("<li><a href=\"").append(href).append("\" target=\"_blank\">")
                                .append(linkText).append("</a></li>");
                        }
                    }
                    desc.append("</ul>");
                }

                // 미첨부사유
                Locator reason = tab3.locator("th:has-text('미첨부사유') + td");
                if (reason.count() > 0) {
                    String reasonText = reason.first().textContent().trim();
                    if (!reasonText.isEmpty()) {
                        desc.append("<p><strong>미첨부사유</strong>: ").append(reasonText).append("</p>");
                    }
                }
            }

            // 5. 공고 URL
            Locator infoLink = detailPage.locator(".infoLink a");
            if (infoLink.count() > 0) {
                String externalUrl = infoLink.first().getAttribute("href");
                String externalText = infoLink.first().textContent().trim();
                if (externalUrl != null && !externalUrl.isEmpty()) {
                    desc.append("<p><strong>공고 URL</strong>: <a href=\"").append(externalUrl)
                        .append("\" target=\"_blank\">").append(externalText).append("</a></p>");
                }
            }

            data.enrichJobDetail(desc.toString(), req.toString(), "");
        } catch (Exception e) {
            log.warn("[잡알리오-Parser] 상세 페이지 보강 실패: {}", e.getMessage());
        }
    }

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
}
