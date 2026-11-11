const { chromium } = require('playwright');
const path = require('path');

(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const page = await browser.newPage();
  await page.route('**/api/dita-validation/recent-input-paths?**', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(['2026/CT1_EV_27MY/Korean', '2026/JA_ICE_PE2_27MY/Korean'])
  }));
  await page.route('**/api/dita-validation/recent-input-paths/delete', route => route.fulfill({ status: 200 }));
  await page.setContent(`
    <base href="http://dita.test/"><main class="revision-page"><input id="inputPath"><div class="recent-path-field">
      <div id="recentPathList" hidden></div></div><input name="executionMode" value="GOMS" checked type="radio">
      <button id="continueBtn"></button><button id="reportListBtn"></button>
      <section id="resultPanel" hidden><p id="resultMessage"></p></section>
    </main>`);
  await page.$eval('#inputPath', input => input.closest('main').querySelector('.recent-path-field').prepend(input));
  await page.addScriptTag({ path: path.resolve('src/main/resources/static/user/js/DitaValidationInput.js') });

  await page.evaluate(() => {
    window.open = (url, name, features) => {
      window.__lastPopup = { url, name, features };
      return {};
    };
  });
  await page.click('#reportListBtn');
  const popup = await page.evaluate(() => window.__lastPopup);
  const expectedPopupWidth = await page.evaluate(() => Math.min(1240, screen.availWidth - 24));
  if (popup.url !== '/dita-validation/reports'
      || !popup.features.includes(`width=${expectedPopupWidth}`)) {
    throw new Error(`리포트 창 너비가 올바르지 않습니다: ${JSON.stringify(popup)}`);
  }

  await page.click('#inputPath');
  await page.waitForFunction(() => document.querySelectorAll('.recent-path-option').length === 2);
  if (await page.$eval('#recentPathList', element => element.hidden)) throw new Error('첫 클릭에서 목록이 열리지 않았습니다.');

  await page.click('#inputPath');
  if (!(await page.$eval('#recentPathList', element => element.hidden))) throw new Error('재클릭에서 목록이 닫히지 않았습니다.');

  await page.click('#inputPath');
  await page.waitForFunction(() => !document.querySelector('#recentPathList').hidden);
  await page.click('.recent-path-option');
  const selected = await page.inputValue('#inputPath');
  if (selected !== '2026/CT1_EV_27MY/Korean') throw new Error('선택한 경로가 입력되지 않았습니다.');
  if (!(await page.$eval('#recentPathList', element => element.hidden))) throw new Error('선택 후 목록이 닫히지 않았습니다.');

  await page.click('#inputPath');
  await page.keyboard.press('Escape');
  if (!(await page.$eval('#recentPathList', element => element.hidden))) throw new Error('Escape로 목록이 닫히지 않았습니다.');

  await browser.close();
  console.log('DITA Validation input browser test passed.');
})().catch(error => {
  console.error(error);
  process.exit(1);
});
