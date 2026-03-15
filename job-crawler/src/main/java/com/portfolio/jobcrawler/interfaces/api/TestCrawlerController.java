package com.portfolio.jobcrawler.interfaces.api;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestCrawlerController {

    @GetMapping("/api/test/saramin")
    public String testSaramin() {
        StringBuilder result = new StringBuilder();
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            Page.NavigateOptions options = new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED);
            page.navigate("https://www.saramin.co.kr/zf_user/jobs/relay/view?isMypage=no&rec_idx=53293016&recommend_ids=eJxNjskNAzEMA6vJn6ROv1PI9t9FjA1g%2BTmgxGGYohf1NPmpb5gJ6HwW9KKq0n2n%2BKdYQB2Ul4WdX3mSfR0bgUGau10Y0OAWlXC8dKU4MxZ3%2B4h6VU%2Bzms17ZIbeGT%2Bp0C%2BA&view_type=list&gz=1&t_ref_content=section_favor_001&t_ref=jobcategory_recruit&t_ref_area=101&relayNonce=1691cf9c1ec0012094ff&immediately_apply_layer_open=n#seq=0", options);
            try { Thread.sleep(3000); } catch (InterruptedException e) {}

            result.append("=== IFRAME CHECK ===\n");
            Locator iframe = page.locator("iframe#iframe_content_0");
            if (iframe.count() > 0) {
                result.append("iframe exists. Inner HTML snippet:\n");
                String html = iframe.first().contentFrame().locator("body").innerHTML();
                result.append(html.substring(0, Math.min(2000, html.length()))).append("\n");
                
                result.append("\n=== IFRAME IMAGES ===\n");
                Locator imgs = iframe.first().contentFrame().locator("img");
                for (int i = 0; i < imgs.count(); i++) {
                    result.append(imgs.nth(i).getAttribute("src")).append("\n");
                }
            } else {
                result.append("NO IFRAME\n");
            }

            result.append("\n=== MAIN WRAP CHECK ===\n");
            Locator wrap = page.locator(".wrap_jv_cont");
            if (wrap.count() > 0) {
                result.append("wrap_jv_cont exists.\n");
                String html = wrap.first().innerHTML();
                result.append(html.substring(0, Math.min(2000, html.length()))).append("\n");
            } else {
                result.append("NO wrap_jv_cont\n");
            }
        }
        return result.toString();
    }
}
