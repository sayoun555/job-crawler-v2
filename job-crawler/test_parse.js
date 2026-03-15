const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto('https://www.saramin.co.kr/zf_user/jobs/relay/view?isMypage=no&rec_idx=53293016&recommend_ids=eJxNjskNAzEMA6vJn6ROv1PI9t9FjA1g%2BTmgxGGYohf1NPmpb5gJ6HwW9KKq0n2n%2BKdYQB2Ul4WdX3mSfR0bgUGau10Y0OAWlXC8dKU4MxZ3%2B4h6VU%2Bzms17ZIbeGT%2Bp0C%2BA&view_type=list&gz=1&t_ref_content=section_favor_001&t_ref=jobcategory_recruit&t_ref_area=101&relayNonce=1691cf9c1ec0012094ff&immediately_apply_layer_open=n#seq=0');
  await page.waitForTimeout(3000);
  const iframeContent = await page.evaluate(() => {
    const iframe = document.querySelector('iframe#iframe_content_0');
    return iframe ? iframe.contentDocument.body.innerHTML.substring(0, 1000) : 'NO_IFRAME';
  });
  console.log("IFRAME_CONTENT:\n" + iframeContent);
  const mainBox = await page.evaluate(() => {
    const box = document.querySelector('.wrap_jv_cont');
    return box ? box.innerHTML.substring(0, 1000) : 'NO_BOX';
  });
  console.log("\nMAIN_BOX_CONTENT:\n" + mainBox);
  await browser.close();
})();
