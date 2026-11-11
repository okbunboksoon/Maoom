const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const page = await browser.newPage();
  await page.route('**/api/dita-validation/goms/reports', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify([{ year: '2026', projects: [{
      projectName: 'DI3_ICE_PE_27MY',
      languages: [{
        languageName: 'Korean',
        gomsPath: '2026/DI3_ICE_PE_27MY/Korean',
        reports: [{ fileName: '261110_1430_report.html', historyId: null,
          editable: false, url: '#report', issueCount: null }]
      }]
    }] }])
  }));
  let html = fs.readFileSync(path.resolve(
    'src/main/resources/templates/user/ditaValidation/reportList.html'), 'utf8');
  html = html.replace('<head>', '<head><base href="http://dita.test/">');
  await page.setContent(html, { waitUntil: 'domcontentloaded' });
  const reportCss = fs.readFileSync(path.resolve(
    'src/main/resources/static/user/css/DitaReportList.css'), 'utf8')
    .replace(/^@import[^\n]*\n/, '');
  await page.addStyleTag({ content: reportCss });

  await page.waitForFunction(() => document.querySelectorAll('.level-year').length === 1);
  if ((await page.textContent('.level-year>.dita-report-folder>.dita-report-folder-name')) !== '2026') {
    throw new Error('연도 폴더가 표시되지 않았습니다.');
  }
  await page.click('.level-year>.dita-report-folder');
  await page.click('.level-project>.dita-report-folder');
  const languageText = await page.textContent('.level-language>.dita-report-folder');
  if (!languageText.includes('Korean')
      || !languageText.includes('GOMS 경로:')
      || !languageText.includes('2026/DI3_ICE_PE_27MY/Korean')) {
    throw new Error('언어 행의 GOMS 경로 표시가 올바르지 않습니다.');
  }
  await page.click('.level-language>.dita-report-folder');
  if ((await page.textContent('.dita-report-file-name')) !== '261110_1430_report.html') {
    throw new Error('언어 report 폴더의 HTML이 표시되지 않았습니다.');
  }
  const margins = await page.evaluate(() => {
    const card = document.querySelector('.dita-report-page').getBoundingClientRect();
    return { left: card.left, right: innerWidth - card.right, bottom: innerHeight - card.bottom };
  });
  if (Math.abs(margins.left - margins.right) > 1
      || Math.abs(margins.left - margins.bottom) > 1) {
    throw new Error(`회색 여백이 동일하지 않습니다: ${JSON.stringify(margins)}`);
  }

  await browser.close();
  console.log('DITA report hierarchy browser test passed.');
})().catch(error => {
  console.error(error);
  process.exit(1);
});
