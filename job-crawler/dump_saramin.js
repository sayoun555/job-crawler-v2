const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    viewport: { width: 1280, height: 800 }
  });
  const page = await context.newPage();
  
  try {
    console.log("Navigating...");
    await page.goto("https://www.saramin.co.kr/zf_user/jobs/relay/view?isMypage=no&rec_idx=53293016", { waitUntil: 'domcontentloaded', timeout: 30000 });
    
    // wait for a bit to let JS render
    await page.waitForTimeout(3000);
    
    const content = await page.content();
    require('fs').writeFileSync('saramin_node_dump.html', content);
    console.log("Dumped to saramin_node_dump.html");
    
    // Check for iframe
    const iframes = await page.$$('iframe');
    console.log("Found " + iframes.length + " iframes");
    
    for (const frame of page.frames()) {
      console.log("Frame URL: " + frame.url());
      if (frame.url().includes('iframe')) {
          const body = await frame.content();
          require('fs').writeFileSync('saramin_node_iframe_dump.html', body);
          console.log("Dumped iframe to saramin_node_iframe_dump.html");
      }
    }
  } catch (e) {
    console.error(e);
  } finally {
    await browser.close();
  }
})();
