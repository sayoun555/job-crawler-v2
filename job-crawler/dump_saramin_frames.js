const { chromium } = require('playwright');

(async () => {
    // 안티봇 우회 설정: 기존 브라우저 프로필 사용 + 헤드리스 끄기
    const browser = await chromium.launchPersistentContext(
        '/Users/sanghyunyoun/Library/Application Support/Google/Chrome', 
        { 
            headless: false,
            channel: 'chrome',
            viewport: null
        }
    );
    
    // add init script to mock webdriver
    await browser.addInitScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");

    const page = await browser.newPage();
    
    try {
        console.log("Navigating to saramin detail...");
        // wait until commit - just wait for first byte, avoid full load timeout
        await page.goto("https://www.saramin.co.kr/zf_user/jobs/relay/view?isMypage=no&rec_idx=53293016", { waitUntil: 'commit', timeout: 30000 });
        
        // Wait 5 seconds for AJAX and frames
        console.log("Waiting 5s for JS rendering...");
        await page.waitForTimeout(5000);
        
        const frames = page.frames();
        console.log(`Found ${frames.length} frames.`);
        
        for (let i = 0; i < frames.length; i++) {
            const frame = frames[i];
            const name = await frame.name();
            const url = frame.url();
            console.log(`Frame ${i}: Name='${name}', URL='${url}'`);
            
            if (url.includes('view-detail') || name.includes('content') || url.includes('iframe') || name.includes('iframe')) {
                const body = await frame.content();
                require('fs').writeFileSync(`frame_${i}.html`, body);
                console.log(`=> Dumped to frame_${i}.html`);
            }
        }
        
    } catch (e) {
        console.error("Error: ", e);
    } finally {
        await browser.close();
    }
})();
