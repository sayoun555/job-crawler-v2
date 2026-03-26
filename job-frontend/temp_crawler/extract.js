const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
  });
  
  try {
    const page = await context.newPage();
    await page.goto('https://www.saramin.co.kr/zf_user/jobs/public/list', { waitUntil: 'domcontentloaded' });
    const saraminCategories = await page.evaluate(() => {
        const items = document.querySelectorAll('#sp_job_cd .depth2 input[type="checkbox"]');
        const results = [];
        items.forEach(el => {
            const label = el.nextElementSibling ? el.nextElementSibling.innerText.trim() : '';
            if(label) results.push({ id: el.value, label: label });
        });
        return results;
    });
    console.log("=== SARAMIN ===");
    saraminCategories.forEach(c => console.log(`${c.label}: ${c.id}`));
  } catch (e) {
    console.error("Saramin error:", e.message);
  }

  try {
    const page2 = await context.newPage();
    await page2.goto('https://www.jobplanet.co.kr/job', { waitUntil: 'domcontentloaded' });
    const jpCategories = await page2.evaluate(() => {
        const items = document.querySelectorAll('.jply_select_box .select_list [data-id]');
        const results = [];
        // Just grab the first select box elements
        items.forEach(el => {
            const label = el.innerText.trim();
            if(label) results.push({ id: el.getAttribute('data-id'), label: label });
        });
        return results;
    });
    console.log("=== JOBPLANET ===");
    jpCategories.slice(0, 30).forEach(c => console.log(`${c.label}: ${c.id}`));
  } catch(e) {
    console.error("JobPlanet error:", e.message);
  }
  
  await browser.close();
})();
