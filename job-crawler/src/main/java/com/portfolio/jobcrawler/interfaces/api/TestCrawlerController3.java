package com.portfolio.jobcrawler.interfaces.api;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import com.portfolio.jobcrawler.infrastructure.crawler.dto.CrawledJobData;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestCrawlerController3 {

    @GetMapping("/api/test/saramindb2")
    public CrawledJobData testSaraminDb() {
        CrawledJobData.CrawledJobDataBuilder builder = CrawledJobData.builder();
        builder.title("TEST JOB").company("TEST CO").url("https://www.saramin.co.kr/zf_user/jobs/relay/view?isMypage=no&rec_idx=53293016&recommend_ids=eJxNjskNAzEMA6vJn6ROv1PI9t9FjA1g%2BTmgxGGYohf1NPmpb5gJ6HwW9KKq0n2n%2BKdYQB2Ul4WdX3mSfR0bgUGau10Y0OAWlXC8dKU4MxZ3%2B4h6VU%2Bzms17ZIbeGT%2Bp0C%2BA&view_type=list&gz=1&t_ref_content=section_favor_001&t_ref=jobcategory_recruit&t_ref_area=101&relayNonce=1691cf9c1ec0012094ff&immediately_apply_layer_open=n#seq=0");

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            var context = browser.newContext();
            Page page = context.newPage();

            try {
                page.setDefaultNavigationTimeout(30000);
                page.navigate("https://www.saramin.co.kr/zf_user/jobs/relay/view?isMypage=no&rec_idx=53293016&recommend_ids=eJxNjskNAzEMA6vJn6ROv1PI9t9FjA1g%2BTmgxGGYohf1NPmpb5gJ6HwW9KKq0n2n%2BKdYQB2Ul4WdX3mSfR0bgUGau10Y0OAWlXC8dKU4MxZ3%2B4h6VU%2Bzms17ZIbeGT%2Bp0C%2BA&view_type=list&gz=1&t_ref_content=section_favor_001&t_ref=jobcategory_recruit&t_ref_area=101&relayNonce=1691cf9c1ec0012094ff&immediately_apply_layer_open=n#seq=0");
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
                
                // wait for iframe
                page.waitForSelector("iframe#iframe_content_0", new Page.WaitForSelectorOptions().setTimeout(5000));
                
                StringBuilder descriptionBuilder = new StringBuilder();
                StringBuilder requirementsBuilder = new StringBuilder();

                Locator iframe = page.locator("iframe#iframe_content_0");
                if (iframe.count() > 0) {
                    var frameLoc = iframe.first().contentFrame();
                    if (frameLoc != null) {
                        try {
                            Locator reqTitle = frameLoc.locator("h3:has-text('자격요건'), h4:has-text('자격요건'), dt:has-text('자격요건')");
                            if (reqTitle.count() > 0) {
                                Locator reqContent = reqTitle.first().locator("~ dd, ~ div, ~ ul").first();
                                if (reqContent.count() > 0) {
                                    requirementsBuilder.append(reqContent.innerText());
                                }
                            }

                            Locator userContent = frameLoc.locator(".user_content");
                            if (userContent.count() > 0) {
                                descriptionBuilder.append(userContent.first().innerText());
                            } else {
                                Locator body = frameLoc.locator("body");
                                if (body.count() > 0) {
                                    descriptionBuilder.append(body.first().innerText());
                                }
                            }
                        } catch (Exception e) {
                            descriptionBuilder.append("IFRAME_PARSE_ERR: ").append(e.getMessage());
                        }
                    } else {
                        descriptionBuilder.append("FRAME_LOC_IS_NULL ");
                    }
                } else {
                    descriptionBuilder.append("IFRAME_NOT_FOUND ");
                }
                
                if (descriptionBuilder.length() <= 20) { // e.g. only errors
                    try {
                        Locator mainContent = page.locator(".wrap_jv_cont, .relay_view, .detail_content");
                        if (mainContent.count() > 0) {
                            descriptionBuilder.append("\nFALLBACK_MAIN: ").append(mainContent.first().innerText());
                        } else {
                            Locator userContent = page.locator(".user_content, .user_detail_content");
                            if (userContent.count() > 0) {
                                descriptionBuilder.append("\nFALLBACK_USER: ").append(userContent.first().innerText());
                            }
                        }
                    } catch (Exception e) {
                        descriptionBuilder.append("\nFALLBACK_ERR: ").append(e.getMessage());
                    }
                }

                builder.description(descriptionBuilder.toString());
                builder.requirements(requirementsBuilder.toString());
            } catch (Exception e) {
                builder.description("NAVIGATE ERR: " + e.getMessage());
            } finally {
                page.close();
            }

            return builder.build();
        } catch (Exception e) {
            builder.description("PLAYWRIGHT ERR: " + e.getMessage());
            return builder.build();
        }
    }
}
