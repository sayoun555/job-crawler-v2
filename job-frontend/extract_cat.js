const { chromium } = require('playwright');
const fs = require('fs');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
  });
  
  try {
    const page = await context.newPage();
    await page.goto('https://www.saramin.co.kr/zf_user/jobs/public/list', { waitUntil: 'domcontentloaded' });
    
    // Extract Saramin job categories
    const saraminCategories = await page.evaluate(() => {
        // Saramin's job category list is usually within #sp_job_cd
        const items = document.querySelectorAll('#sp_job_cd .depth2 input[type="checkbox"]');
        const results = [];
        items.forEach(el => {
            const label = el.nextElementSibling ? el.nextElementSibling.innerText.trim() : '';
            results.push({ id: el.value, label: label });
        });
        return results;
    });
    console.log("Saramin Categories:", saraminCategories.slice(0, 10)); // print first 10
  } catch (e) {
    console.error("Saramin error:", e.message);
  }

  try {
    const page2 = await context.newPage();
    await page2.goto('https://www.jobplanet.co.kr/job', { waitUntil: 'domcontentloaded' });
    
    // Extract JobPlanet job categories
    const jpCategories = await page2.evaluate(() => {
        const items = document.querySelectorAll('.jply_select_box .select_list [data-id]');
        const results = [];
        items.forEach(el => {
            results.push({ id: el.getAttribute('data-id'), label: el.innerText.trim() });
        });
        return results;
    });
    console.log("JobPlanet Categories:", jpCategories.slice(0, 10)); // print first 10
  } catch(e) {
    console.error("JobPlanet error:", e.message);
  }
  
  await browser.close();
})();
