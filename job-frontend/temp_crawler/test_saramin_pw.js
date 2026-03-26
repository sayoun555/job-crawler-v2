const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
  });
  
  try {
    const page = await context.newPage();
    console.log("Navigating...");
    await page.goto('https://www.saramin.co.kr/zf_user/search/recruit?searchType=search&recruitPage=1&recruitSort=relation&recruitPageCount=40&job_cd=84&job_mid_cd=2', { waitUntil: 'domcontentloaded' });
    
    await page.waitForTimeout(3000);
    const count = await page.locator("div.item_recruit").count();
    console.log("Saramin div.item_recruit count:", count);
    
    if (count === 0) {
        console.log("Checking body content...", (await page.content()).substring(0, 500));
    } else {
        const text = await page.locator("div.item_recruit").first().innerText();
        console.log("First item:", text.substring(0, 100));
    }
  } catch (e) {
    console.error("Saramin error:", e.message);
  }
  await browser.close();
})();
