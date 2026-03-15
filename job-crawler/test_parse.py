from playwright.sync_api import sync_playwright
import time

def run(playwright):
    browser = playwright.chromium.launch(headless=True)
    context = browser.new_context()
    page = context.new_page()
    url = "https://www.saramin.co.kr/zf_user/jobs/relay/view?isMypage=no&rec_idx=53293016&recommend_ids=eJxNjskNAzEMA6vJn6ROv1PI9t9FjA1g%2BTmgxGGYohf1NPmpb5gJ6HwW9KKq0n2n%2BKdYQB2Ul4WdX3mSfR0bgUGau10Y0OAWlXC8dKU4MxZ3%2B4h6VU%2Bzms17ZIbeGT%2Bp0C%2BA&view_type=list&gz=1&t_ref_content=section_favor_001&t_ref=jobcategory_recruit&t_ref_area=101&relayNonce=1691cf9c1ec0012094ff&immediately_apply_layer_open=n#seq=0"
    print(f"Navigating to {url}...")
    page.goto(url)
    time.sleep(3)
    
    print("\n=== IFRAME CHECK ===")
    iframe_element = page.locator("iframe#iframe_content_0")
    if iframe_element.count() > 0:
        print("iframe exists. Inner HTML snippet:")
        frame = iframe_element.first.content_frame
        if frame:
            body = frame.locator("body").inner_html()
            print(body[:2000])
            print("\n=== IFRAME IMAGES ===")
            imgs = frame.locator("img").all()
            for img in imgs:
                print(img.get_attribute("src"))
        else:
            print("Frame content not accessible (cross-origin?).")
    else:
        print("NO IFRAME")
        
    print("\n=== MAIN WRAP CHECK ===")
    wrap = page.locator(".wrap_jv_cont")
    if wrap.count() > 0:
        print("wrap_jv_cont exists.")
        print(wrap.first.inner_html()[:2000])
    elif page.locator(".user_content").count() > 0:
        print("user_content exists.")
        print(page.locator(".user_content").first.inner_html()[:2000])
    else:
        print("NO wrap_jv_cont OR user_content")
        
    browser.close()

with sync_playwright() as playwright:
    run(playwright)
