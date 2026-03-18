package com.portfolio.jobcrawler.infrastructure.crawler.parser;

import com.microsoft.playwright.FrameLocator;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.portfolio.jobcrawler.infrastructure.crawler.dto.CrawledJobData;
import com.portfolio.jobcrawler.global.util.HtmlSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.portfolio.jobcrawler.infrastructure.crawler.parser.category.SaraminJobCategory;

/**
 * 사람인 DOM 파싱 전담 클래스 (직업별 채용정보 페이지 기반).
 * - URL: /zf_user/jobs/list/job-category?cat_mcls=2 (IT개발·데이터)
 * - 광고 공고 제외 (list_item.effect 클래스)
 * - 마감일 지난 공고 필터링
 * - 상세 페이지에서 급여, 지원방법, 이미지 등 추출
 */
@Slf4j
@Component
public class SaraminParser implements SiteParser {

    private static final String SARAMIN_BASE_URL = "https://www.saramin.co.kr";

    @Override
    public String getSiteName() {
        return "SARAMIN";
    }

    @Override
    public String buildSearchUrl(String keyword, String jobCategory) {
        StringBuilder urlBuilder = new StringBuilder(SARAMIN_BASE_URL);
        // 실제 사람인 job-category 페이지 파라미터 (page, page_count, sort)
        urlBuilder.append("/zf_user/jobs/list/job-category?cat_mcls=2");
        urlBuilder.append("&search_optional_item=n&search_done=y&panel_count=y&preview=y");
        urlBuilder.append("&page=1&page_count=50&sort=RD");
        urlBuilder.append("&type=job-category&is_param=1&isSearchResultEmpty=1&isSectionHome=0&searchParamCount=1");

        if (keyword != null && !keyword.isBlank()) {
            try {
                String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8.toString());
                urlBuilder.append("&searchword=").append(encodedKeyword);
            } catch (Exception e) {
                // Ignore encode error
            }
        }

        if (jobCategory != null && !jobCategory.isBlank() && !jobCategory.equals("전체") && !jobCategory.equals("all")) {
            String catCode = SaraminJobCategory.getCodeByDisplayName(jobCategory);
            if (catCode != null) {
                urlBuilder.append("&cat_scls=").append(catCode);
            }
        }

        return urlBuilder.toString();
    }

    @Override
    public Locator getListItems(Page page) {
        // .list_recruiting .list_body 내의 실제 공고만 선택
        // 광고(list_item.effect) 제외, 큐레이션 영역(list_curation_wrap)은 .list_item이 아니므로 자동 제외
        return page.locator(".list_recruiting .list_body > .list_item:not(.effect) > .box_item");
    }

    @Override
    public void waitForListLoaded(Page page) {
        try {
            page.waitForSelector(".list_recruiting .list_body .list_item .box_item",
                    new Page.WaitForSelectorOptions().setTimeout(15000));
        } catch (Exception e) {
            log.warn("[사람인-Parser] 공고 목록 로딩 지연 또는 없음: {}", e.getMessage());
        }
    }

    @Override
    public boolean goToNextPage(Page page, int currentPageNum) {
        int nextPage = currentPageNum + 1;

        // .PageBox에서 다음 페이지 버튼 클릭 (page="N" 속성 사용)
        Locator nextBtn = page.locator(".PageBox button[page='" + nextPage + "']");
        if (nextBtn.count() > 0) {
            log.info("[사람인-Parser] {}페이지 버튼 클릭", nextPage);
            nextBtn.first().click();
            waitForListLoaded(page);
            Locator items = getListItems(page);
            return items.count() > 0;
        }

        // 다음 범위 버튼 (11페이지 이상)
        Locator nextRangeBtn = page.locator(".PageBox button.BtnNext");
        if (nextRangeBtn.count() > 0) {
            log.info("[사람인-Parser] '다음' 버튼 클릭 ({}페이지 이동)", nextPage);
            nextRangeBtn.first().click();
            waitForListLoaded(page);
            Locator items = getListItems(page);
            return items.count() > 0;
        }

        log.info("[사람인-Parser] 더 이상 페이지 없음");
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
        String title = extractTitle(item);
        if (title.isEmpty()) return null;

        String deadline = safeText(item, ".support_detail .date");
        if (deadline.isEmpty()) deadline = safeText(item, ".support_info .date");
        if (isDeadlineExpired(deadline)) {
            log.debug("[사람인-Parser] 마감된 공고 스킵: {}", title);
            return null;
        }

        String company = extractCompany(item);
        String companyLogoUrl = extractCompanyLogo(item);
        String url = extractJobUrl(item);
        String location = safeText(item, ".recruit_info .work_place");
        String career = safeText(item, ".recruit_info .career");
        String education = safeText(item, ".recruit_info .education");
        String techStack = extractSkillTags(item);

        String parsedCategory = extractJobCategory(item);
        String finalCategory;
        if (requestedJobCategory != null && !requestedJobCategory.isBlank() && !requestedJobCategory.equals("전체") && !requestedJobCategory.equals("all")) {
            finalCategory = requestedJobCategory;
        } else {
            finalCategory = normalizeCategory(parsedCategory);
        }

        return CrawledJobData.builder()
                .title(title).company(company).companyLogoUrl(companyLogoUrl)
                .location(location).url(url).sourceSite(getSiteName())
                .applicationMethod("UNKNOWN").education(education).career(career)
                .salary("").deadline(deadline).techStack(techStack)
                .jobCategory(finalCategory)
                .description("").requirements("").companyImages("")
                .build();
    }

    @Override
    public void enrichFromDetailPage(Page detailPage, CrawledJobData data) {
        try {
            // iframe 로딩 대기
            try {
                detailPage.waitForSelector("iframe#iframe_content_0",
                        new Page.WaitForSelectorOptions().setTimeout(5_000));
                detailPage.waitForTimeout(500);
            } catch (Exception e) {
                log.warn("[사람인-Parser] iframe 로딩 지연: {}", e.getMessage());
            }

            StringBuilder descriptionBuilder = new StringBuilder();
            StringBuilder requirementsBuilder = new StringBuilder();
            List<String> imageUrls = new ArrayList<>();

            extractKeyInfoForData(detailPage, data, descriptionBuilder, requirementsBuilder);
            extractFromIframeIfExists(detailPage, descriptionBuilder, requirementsBuilder, imageUrls);
            extractOuterImages(detailPage, imageUrls);
            extractMainContentFallback(detailPage, descriptionBuilder);
            extractApplicationMethodForData(detailPage, data);
            applyFormattedResultToData(data, descriptionBuilder.toString(), requirementsBuilder.toString(), imageUrls);
        } catch (Exception e) {
            log.warn("[사람인-Parser] 상세 페이지 보강 실패: {}", e.getMessage());
        }
    }

    @Override
    public CrawledJobData parseJobData(Page listPage, Locator item, String requestedJobCategory) {
        CrawledJobData data = parseListData(listPage, item, requestedJobCategory);
        if (data == null || data.getUrl() == null || data.getUrl().isBlank()) return data;

        Page detailPage = listPage.context().newPage();
        try {
            detailPage.setDefaultNavigationTimeout(15_000);
            detailPage.navigate(data.getUrl(), new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
            enrichFromDetailPage(detailPage, data);
        } catch (Exception e) {
            log.warn("[사람인-Parser] 상세 페이지 크롤링 실패 ({}): {}", data.getUrl(), e.getMessage());
        } finally {
            detailPage.close();
        }
        return data;
    }

    // ========== 리스트 페이지 파싱 ==========

    private String normalizeCategory(String raw) {
        return SaraminJobCategory.normalizeCategory(raw);
    }

    private String extractTitle(Locator item) {
        String title = safeText(item, ".notification_info .job_tit a.str_tit");
        if (title.isEmpty()) title = safeText(item, ".job_tit a.str_tit");
        if (title.isEmpty()) title = safeText(item, ".job_tit a span");
        if (title.isEmpty()) title = safeText(item, ".job_tit a");
        return title;
    }

    private String extractCompany(Locator item) {
        String company = safeText(item, ".company_nm .str_tit");
        if (company.isEmpty()) company = safeText(item, ".corp_name a");
        if (company.isEmpty()) company = safeText(item, ".company_nm a");
        return company;
    }

    private String extractCompanyLogo(Locator item) {
        String logoUrl = safeAttr(item, ".company_nm .logo img", "src");
        if (logoUrl == null || logoUrl.isEmpty()) {
            logoUrl = safeAttr(item, ".corp_logo img", "src");
        }
        return logoUrl != null ? logoUrl : "";
    }

    private String extractJobUrl(Locator item) {
        String jobUrl = safeAttr(item, ".notification_info .job_tit a.str_tit", "href");
        if (jobUrl == null || jobUrl.isEmpty()) {
            jobUrl = safeAttr(item, ".job_tit a", "href");
        }

        if (jobUrl == null || jobUrl.isEmpty()) return null;
        if (jobUrl.startsWith("/")) jobUrl = SARAMIN_BASE_URL + jobUrl;

        try {
            if (jobUrl.contains("/zf_user/jobs/relay/view")) {
                if (!jobUrl.startsWith("http")) jobUrl = SARAMIN_BASE_URL + jobUrl;
                // 광고 트래킹 파라미터 제거, 해시 제거
                return jobUrl.split("#")[0]
                        .replaceAll("&adsCategoryItem=[^&]*", "");
            }

            Matcher matcher = Pattern.compile("rec_idx=([0-9]+)").matcher(jobUrl);
            if (matcher.find()) {
                return SARAMIN_BASE_URL + "/zf_user/jobs/relay/view?rec_idx=" + matcher.group(1);
            }
        } catch (Exception ignored) {}
        return jobUrl;
    }

    private String extractJobCategory(Locator item) {
        Locator tags = item.locator(".job_sector span");
        if (tags.count() == 0) tags = item.locator(".job_meta .job_sector span");

        if (tags.count() > 0) {
            String firstTag = safeText(tags.first());
            if (!firstTag.isEmpty() && !firstTag.equals("외")) return firstTag;
        }
        return "";
    }

    private String extractSkillTags(Locator item) {
        Locator tags = item.locator(".job_sector span");
        if (tags.count() == 0) return "";

        List<String> skills = new ArrayList<>();
        for (int i = 0; i < Math.min(tags.count(), 10); i++) {
            String tag = safeText(tags.nth(i));
            if (!tag.isEmpty() && !tag.equals("외")) {
                skills.add(tag);
            }
        }
        return String.join(",", skills);
    }

    // ========== 마감일 체크 ==========

    /**
     * 마감일 텍스트를 파싱하여 이미 지난 공고인지 판별.
     * 형식: ~MM.DD(요일), D-N, 오늘마감, 내일마감, 채용시, 상시채용, 마감
     */
    private boolean isDeadlineExpired(String deadline) {
        if (deadline == null || deadline.isBlank()) return false;

        String trimmed = deadline.trim();

        // 상시 채용 → 항상 유효
        if (trimmed.contains("채용시") || trimmed.contains("상시")) return false;

        // D-day 형식 (D-26 등) → 아직 유효
        if (trimmed.matches(".*D-\\d+.*")) return false;

        // 오늘/내일 마감 → 아직 유효
        if (trimmed.contains("오늘마감") || trimmed.contains("내일마감") || trimmed.contains("오늘")) return false;

        // 명시적 마감
        if (trimmed.equals("마감") || trimmed.contains("접수마감") || trimmed.contains("마감됨")) return true;

        // ~YYYY.MM.DD 형식
        Pattern fullDatePattern = Pattern.compile("~?(\\d{4})\\.(\\d{2})\\.(\\d{2})");
        Matcher fullMatcher = fullDatePattern.matcher(trimmed);
        if (fullMatcher.find()) {
            try {
                int year = Integer.parseInt(fullMatcher.group(1));
                int month = Integer.parseInt(fullMatcher.group(2));
                int day = Integer.parseInt(fullMatcher.group(3));
                return LocalDate.of(year, month, day).isBefore(LocalDate.now());
            } catch (Exception e) {
                return false;
            }
        }

        // ~MM.DD(요일) 형식 (연도 없음)
        Pattern shortDatePattern = Pattern.compile("~?(\\d{2})\\.(\\d{2})");
        Matcher shortMatcher = shortDatePattern.matcher(trimmed);
        if (shortMatcher.find()) {
            try {
                int month = Integer.parseInt(shortMatcher.group(1));
                int day = Integer.parseInt(shortMatcher.group(2));
                LocalDate deadlineDate = LocalDate.of(LocalDate.now().getYear(), month, day);
                // 6개월 이상 과거면 내년으로 간주
                if (deadlineDate.isBefore(LocalDate.now().minusMonths(6))) {
                    deadlineDate = deadlineDate.plusYears(1);
                }
                return deadlineDate.isBefore(LocalDate.now());
            } catch (Exception e) {
                return false;
            }
        }

        return false; // 판별 불가 → 유효로 간주
    }

    // ========== 상세 페이지 보강 ==========

    /**
     * 상세 페이지 핵심 정보 (경력, 학력, 급여, 근무지 등) 추출.
     * CrawledJobData에 직접 값을 세팅한다.
     */
    private void extractKeyInfoForData(Page detailPage, CrawledJobData data,
                                       StringBuilder descBuilder, StringBuilder reqBuilder) {
        Locator summaryDls = detailPage.locator(".jv_summary .cont dl");
        if (summaryDls.count() == 0) {
            summaryDls = detailPage.locator(".jv_header .col dl");
        }
        if (summaryDls.count() == 0) return;

        StringBuilder headerBuilder = new StringBuilder("<h3>채용 요약</h3><ul>");

        for (int i = 0; i < summaryDls.count(); i++) {
            String dt = safeText(summaryDls.nth(i).locator("dt"));
            if (dt.isEmpty()) continue;

            if (dt.contains("우대사항")) {
                String preferred = extractPreferredQualifications(summaryDls.nth(i));
                if (!preferred.isEmpty()) {
                    headerBuilder.append("<li><strong>우대사항</strong>: ").append(preferred).append("</li>");
                    reqBuilder.append("[우대사항]\n").append(preferred).append("\n");
                }
                continue;
            }

            String dd = safeText(summaryDls.nth(i).locator("dd"));
            if (dd.isEmpty()) continue;

            headerBuilder.append("<li><strong>").append(dt).append("</strong>: ").append(dd).append("</li>");

            if (dt.contains("급여") || dt.contains("연봉")) {
                data.setSalary(dd);
            }
            if (dt.contains("자격요건") || dt.contains("지원자격")) {
                reqBuilder.append(dd).append("\n");
            }
        }

        headerBuilder.append("</ul>");
        descBuilder.insert(0, headerBuilder.toString());
    }

    /**
     * 우대사항 dd.preferred 내 숨겨진 .toolTipTxt li에서 실제 내용 추출.
     * 각 li: <span>카테고리</span>값 형태
     */
    private String extractPreferredQualifications(Locator dl) {
        Locator items = dl.locator("dd.preferred .toolTipTxt li");
        if (items.count() == 0) {
            // 폴백: 일반 dd 텍스트
            String fallback = safeText(dl.locator("dd"));
            if (fallback.contains("상세보기")) return "";
            return fallback;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.count(); i++) {
            String category = safeText(items.nth(i).locator("span"));
            String fullText = safeText(items.nth(i));
            // fullText = "카테고리값" → 카테고리 부분 제거하면 값만 남음
            String value = fullText;
            if (!category.isEmpty() && fullText.startsWith(category)) {
                value = fullText.substring(category.length()).trim();
            }
            if (!category.isEmpty()) {
                sb.append(category).append(": ").append(value);
            } else {
                sb.append(value);
            }
            if (i < items.count() - 1) sb.append(", ");
        }
        return sb.toString();
    }

    private void extractApplicationMethodForData(Page detailPage, CrawledJobData data) {
        String method = safePageText(detailPage, ".jv_howto dl.guide dd.method");
        if (method.isEmpty()) {
            method = safePageText(detailPage, ".jv_howto .cont");
        }
        if (method.isEmpty()) {
            method = safePageText(detailPage, ".wrap_jv_cont");
        }

        if (method.contains("사람인 입사지원") || method.contains("온라인 입사지원")) {
            data.setApplicationMethod("SARAMIN_APPLY");
        } else if (method.contains("홈페이지 지원") || method.contains("홈페이지")) {
            data.setApplicationMethod("HOMEPAGE");
        } else if (method.contains("이메일")) {
            data.setApplicationMethod("EMAIL");
        }
    }

    private void extractFromIframeIfExists(Page detailPage, StringBuilder descBuilder, StringBuilder reqBuilder, List<String> imageUrls) {
        Locator iframe = detailPage.locator("iframe#iframe_content_0");
        if (iframe.count() == 0) return;

        try {
            FrameLocator frameLoc = iframe.first().contentFrame();
            if (frameLoc == null) return;

            // iframe 내부 콘텐츠 로딩 대기
            try {
                frameLoc.locator(".user_content, body").first().waitFor(
                        new Locator.WaitForOptions().setTimeout(5_000));
            } catch (Exception e) {
                log.warn("[사람인-Parser] iframe 내부 콘텐츠 대기 초과");
            }

            Locator reqTitle = frameLoc.locator("h3:has-text('자격요건'), h4:has-text('자격요건'), dt:has-text('자격요건')");
            if (reqTitle.count() > 0) {
                Locator reqContent = reqTitle.first().locator("~ dd, ~ div, ~ ul").first();
                if (reqContent.count() > 0) reqBuilder.append(safeText(reqContent));
            }

            // HTML 구조 보존: innerHTML로 가져와서 Jsoup으로 소독
            Locator userContent = frameLoc.locator(".user_content");
            if (userContent.count() > 0) {
                String html = userContent.first().innerHTML();
                descBuilder.append(HtmlSanitizer.sanitize(html));
            } else {
                Locator body = frameLoc.locator("body");
                if (body.count() > 0) {
                    String html = body.first().innerHTML();
                    descBuilder.append(HtmlSanitizer.sanitize(html));
                }
            }

            // iframe 내 이미지 추출
            Locator iframeImages = frameLoc.locator("img");
            log.debug("[사람인-Parser] iframe 내 이미지 수: {}", iframeImages.count());
            extractImagesFromLocator(iframeImages, imageUrls);
        } catch (Exception e) {
            log.warn("[사람인-Parser] iframe 추출 실패: {}", e.getMessage());
        }
    }

    private void extractOuterImages(Page detailPage, List<String> imageUrls) {
        Locator outerImgs = detailPage.locator(".wrap_jv_cont img, .user_content img");
        extractImagesFromLocator(outerImgs, imageUrls);
    }

    private void extractImagesFromLocator(Locator images, List<String> imageUrls) {
        for (int i = 0; i < images.count(); i++) {
            Locator img = images.nth(i);
            String src = img.getAttribute("src");
            if (src == null || !src.startsWith("http") || isAdImage(src)) continue;

            try {
                var box = img.boundingBox();
                if (box != null && (box.width < 200 || box.height < 100)) continue;
            } catch (Exception ignored) {}

            imageUrls.add(src);
        }
    }

    private void extractMainContentFallback(Page detailPage, StringBuilder descBuilder) {
        if (descBuilder.length() > 0) return;

        Locator mainContent = detailPage.locator(".wrap_jv_cont, .relay_view, .detail_content");
        if (mainContent.count() > 0) {
            descBuilder.append(HtmlSanitizer.sanitize(mainContent.first().innerHTML()));
            return;
        }

        Locator userContent = detailPage.locator(".user_content, .user_detail_content");
        if (userContent.count() > 0) {
            descBuilder.append(HtmlSanitizer.sanitize(userContent.first().innerHTML()));
        }
    }

    private void applyFormattedResultToData(
            CrawledJobData data,
            String rawDescription,
            String rawRequirements,
            List<String> imageUrls) {

        String description = rawDescription.trim().replaceAll("\\n{3,}", "\n\n");
        if (description.length() > 5000) {
            description = description.substring(0, 5000) + "...";
        }

        String requirements = rawRequirements.trim().replaceAll("\\n{3,}", "\n\n");
        if (requirements.length() > 2000) {
            requirements = requirements.substring(0, 2000) + "...";
        }

        if (requirements.isEmpty() && !description.isEmpty()) {
            requirements = extractRequirementsFromDescription(description);
        }

        String companyImages = String.join(",", imageUrls.stream().distinct().limit(10).toList());

        data.setDescription(description);
        data.setRequirements(requirements);
        data.setCompanyImages(companyImages);
    }

    private String extractRequirementsFromDescription(String description) {
        // HTML인 경우 텍스트로 변환 후 추출
        String plainText = description.contains("<") ? HtmlSanitizer.toPlainText(description) : description;

        StringBuilder extracted = new StringBuilder();
        String[] lines = plainText.split("\n");
        boolean inReqs = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.contains("자격요건") || trimmed.contains("지원자격") || trimmed.contains("지원 자격")) {
                inReqs = true;
            } else if (inReqs && isSectionEndKeyword(trimmed)) {
                break;
            }

            if (inReqs) {
                extracted.append(trimmed).append("\n");
            }
        }
        return extracted.toString().trim();
    }

    private boolean isSectionEndKeyword(String line) {
        return line.contains("우대사항") || line.contains("근무조건") ||
               line.contains("복리후생") || line.contains("접수기간");
    }

    private boolean isAdImage(String url) {
        if (url == null) return true;
        String lowerUrl = url.toLowerCase();
        // 공고 본문 이미지(pds.saramin.co.kr/recruit/)는 반드시 허용
        if (lowerUrl.contains("pds.saramin.co.kr/recruit/")) return false;
        return lowerUrl.contains("saraminimage.co.kr/store/") ||
               lowerUrl.contains("saraminbanner.co.kr") ||
               lowerUrl.contains("/sri/resize/") ||
               lowerUrl.contains("blank.png") ||
               lowerUrl.contains("btn_") ||
               lowerUrl.contains("pixel") ||
               lowerUrl.contains("spacer");
    }

    // ========== 유틸리티 ==========

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

    private String safeText(Locator locator) {
        try {
            String text = locator.innerText();
            return text != null ? text.trim() : "";
        } catch (Exception ignored) {}
        return "";
    }

    private String safePageText(Page page, String selector) {
        try {
            Locator loc = page.locator(selector);
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
