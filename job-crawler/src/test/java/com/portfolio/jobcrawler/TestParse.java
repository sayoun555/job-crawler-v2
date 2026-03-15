package com.portfolio.jobcrawler;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

public class TestParse {
    public static void main(String[] args) {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.navigate("https://www.saramin.co.kr/zf_user/jobs/relay/view?isMypage=no&rec_idx=53293016&recommend_ids=eJxNjskNAzEMA6vJn6ROv1PI9t9FjA1g%2BTmgxGGYohf1NPmpb5gJ6HwW9KKq0n2n%2BKdYQB2Ul4WdX3mSfR0bgUGau10Y0OAWlXC8dKU4MxZ3%2B4h6VU%2Bzms17ZIbeGT%2Bp0C%2BA&view_type=list&gz=1&t_ref_content=section_favor_001&t_ref=jobcategory_recruit&t_ref_area=101&relayNonce=1691cf9c1ec0012094ff&immediately_apply_layer_open=n#seq=0");
            try { Thread.sleep(3000); } catch (InterruptedException e) {}

            System.out.println("=== IFRAME CHECK ===");
            Locator iframe = page.locator("iframe#iframe_content_0");
            if (iframe.count() > 0) {
                System.out.println("iframe exists. Inner HTML snippet:");
                System.out.println(iframe.first().contentFrame().locator("body").innerHTML().substring(0, Math.min(500, iframe.first().contentFrame().locator("body").innerHTML().length())));
            } else {
                System.out.println("NO IFRAME");
            }

            System.out.println("\n=== MAIN WRAP CHECK ===");
            Locator wrap = page.locator(".wrap_jv_cont");
            if (wrap.count() > 0) {
                System.out.println("wrap_jv_cont exists.");
            } else {
                System.out.println("NO wrap_jv_cont");
            }
        }
    }
}
