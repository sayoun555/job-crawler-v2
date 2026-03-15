package com.portfolio.jobcrawler.interfaces.api;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.portfolio.jobcrawler.infrastructure.crawler.dto.CrawledJobData;
import com.portfolio.jobcrawler.infrastructure.crawler.parser.SaraminParser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
public class TestCrawlerController2 {
    
    @Autowired
    private SaraminParser parser;

    @GetMapping("/api/test/saramindb")
    public CrawledJobData testSaraminDb() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            var context = browser.newContext();
            Page page = context.newPage();
            
            // 더미 리스트 페이지 대신 이 url을 바로 parseJobData에 넣을 순 없으므로 
            // enrichWithDetailData 로직을 직접 시뮬레이션
            CrawledJobData.CrawledJobDataBuilder builder = CrawledJobData.builder();
            builder.title("TEST JOB").company("TEST CO").url("https://www.saramin.co.kr/zf_user/jobs/relay/view?isMypage=no&rec_idx=53293016&recommend_ids=eJxNjskNAzEMA6vJn6ROv1PI9t9FjA1g%2BTmgxGGYohf1NPmpb5gJ6HwW9KKq0n2n%2BKdYQB2Ul4WdX3mSfR0bgUGau10Y0OAWlXC8dKU4MxZ3%2B4h6VU%2Bzms17ZIbeGT%2Bp0C%2BA&view_type=list&gz=1&t_ref_content=section_favor_001&t_ref=jobcategory_recruit&t_ref_area=101&relayNonce=1691cf9c1ec0012094ff&immediately_apply_layer_open=n#seq=0");
            
            // 리플렉션을 사용하기 귀찮으니 바로 내부 로직을 복사해서 실행
            Page detailPage = context.newPage();
            try {
                detailPage.setDefaultNavigationTimeout(30_000);
                Page.NavigateOptions options = new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED);
                detailPage.navigate("https://www.saramin.co.kr/zf_user/jobs/relay/view?isMypage=no&rec_idx=53293016&recommend_ids=eJxNjskNAzEMA6vJn6ROv1PI9t9FjA1g%2BTmgxGGYohf1NPmpb5gJ6HwW9KKq0n2n%2BKdYQB2Ul4WdX3mSfR0bgUGau10Y0OAWlXC8dKU4MxZ3%2B4h6VU%2Bzms17ZIbeGT%2Bp0C%2BA&view_type=list&gz=1&t_ref_content=section_favor_001&t_ref=jobcategory_recruit&t_ref_area=101&relayNonce=1691cf9c1ec0012094ff&immediately_apply_layer_open=n#seq=0", options);

                StringBuilder descriptionBuilder = new StringBuilder();
                StringBuilder requirementsBuilder = new StringBuilder();

                Locator iframe = detailPage.locator("iframe#iframe_content_0");
                if (iframe.count() > 0) {
                    var frameLoc = iframe.first().contentFrame();
                    if (frameLoc != null) {
                        Locator reqTitle = frameLoc.locator("h3:has-text('자격요건'), h4:has-text('자격요건'), dt:has-text('자격요건')");
                        if (reqTitle.count() > 0) {
                            Locator reqContent = reqTitle.first().locator("~ dd, ~ div, ~ ul").first();
                            if (reqContent.count() > 0) requirementsBuilder.append(reqContent.innerText());
                        }

                        Locator userContent = frameLoc.locator(".user_content");
                        if (userContent.count() > 0) {
                            descriptionBuilder.append(userContent.first().innerText());
                        } else {
                            descriptionBuilder.append(frameLoc.locator("body").first().innerText());
                        }
                    }
                }

                if (descriptionBuilder.length() == 0) {
                    Locator mainContent = detailPage.locator(".wrap_jv_cont, .relay_view, .detail_content");
                    if (mainContent.count() > 0) {
                        descriptionBuilder.append(mainContent.first().innerText());
                    } else {
                        Locator userContent = detailPage.locator(".user_content, .user_detail_content");
                        if (userContent.count() > 0) {
                            descriptionBuilder.append(userContent.first().innerText());
                        }
                    }
                }

                builder.description(descriptionBuilder.toString());
                builder.requirements(requirementsBuilder.toString());
            } catch (Exception e) {
                builder.description("FAILED: " + e.getMessage());
            } finally {
                detailPage.close();
            }

            return builder.build();
        } catch (Exception e) {
            return CrawledJobData.builder().description("ERR " + e.getMessage()).build();
        }
    }
}
