const fs = require('fs');
const path = require('path');

const resultsPath = path.join(__dirname, 'test-results', 'results.json');
const screenshotDir = path.join(__dirname, 'test-results', 'screenshots');
const outputPath = path.join(__dirname, 'test-results', 'report.html');

const results = JSON.parse(fs.readFileSync(resultsPath, 'utf8'));

const files = fs.readdirSync(screenshotDir).filter(f => f.endsWith('.png'));

// Precise mapping: test title -> screenshot filenames
const testScreenshotMap = {
  // Loan List
  'displays all test loans in the table': ['loan-list-all-loans.png'],
  'shows correct financial data and status': ['loan-list-financial-data.png'],
  'navigates to loan detail when clicking view icon': ['loan-list-before-nav.png', 'loan-list-after-nav.png'],
  // Loan Detail
  'displays loan summary card with correct data': ['loan-detail-summary.png'],
  'displays property information': ['loan-detail-property.png'],
  'displays payment history table with correct rows': ['loan-detail-payments.png'],
  // Borrower List
  'displays all test borrowers in the table': ['borrower-list-all-borrowers.png'],
  'shows borrower details in columns': ['borrower-list-details.png'],
  'navigates to borrower detail when clicking view icon': ['borrower-list-before-nav.png', 'borrower-list-after-nav.png'],
  // Borrower Detail
  'displays contact information card': ['borrower-detail-contact.png'],
  'displays financial profile card': ['borrower-detail-financial.png'],
  'displays associated loans table': ['borrower-detail-loans.png'],
};

// Build test data
const suites = [];
for (const fileSuite of results.suites) {
  for (const suite of fileSuite.suites) {
    const tests = [];
    for (const spec of suite.specs) {
      const test = spec.tests[0];
      const result = test.results[0];
      const status = result.status;
      const duration = result.duration;
      const title = spec.title;
      const screenshots = testScreenshotMap[title] || [];
      tests.push({ title, status, duration, screenshots });
    }
    suites.push({ file: fileSuite.title, name: suite.title, tests });
  }
}

const totalTests = suites.reduce((sum, s) => sum + s.tests.length, 0);
const passedTests = suites.reduce((sum, s) => sum + s.tests.filter(t => t.status === 'passed').length, 0);
const failedTests = totalTests - passedTests;
const totalDuration = (results.stats.duration / 1000).toFixed(1);

const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Playwright Test Report</title>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f5f5f5; color: #333; line-height: 1.6; }
    .header { background: linear-gradient(135deg, #1a237e 0%, #283593 100%); color: white; padding: 32px; }
    .header h1 { font-size: 28px; margin-bottom: 8px; }
    .header .subtitle { opacity: 0.85; font-size: 14px; }
    .stats { display: flex; gap: 24px; padding: 24px 32px; background: white; border-bottom: 1px solid #e0e0e0; }
    .stat { text-align: center; }
    .stat .value { font-size: 32px; font-weight: 700; }
    .stat .label { font-size: 12px; text-transform: uppercase; color: #666; }
    .stat.passed .value { color: #2e7d32; }
    .stat.failed .value { color: #c62828; }
    .stat.duration .value { color: #1565c0; }
    .container { max-width: 1200px; margin: 24px auto; padding: 0 16px; }
    .suite { background: white; border-radius: 8px; margin-bottom: 24px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); overflow: hidden; }
    .suite-header { padding: 16px 24px; background: #fafafa; border-bottom: 1px solid #e0e0e0; }
    .suite-header h2 { font-size: 18px; color: #1a237e; }
    .suite-header .file { font-size: 12px; color: #888; font-family: monospace; }
    .test { border-bottom: 1px solid #f0f0f0; }
    .test:last-child { border-bottom: none; }
    .test-header { padding: 16px 24px; display: flex; align-items: center; gap: 12px; }
    .badge { display: inline-block; padding: 2px 10px; border-radius: 12px; font-size: 12px; font-weight: 600; text-transform: uppercase; }
    .badge.passed { background: #e8f5e9; color: #2e7d32; }
    .badge.failed { background: #ffebee; color: #c62828; }
    .test-title { font-size: 15px; flex: 1; }
    .test-duration { font-size: 12px; color: #999; }
    .screenshots { padding: 0 24px 16px 24px; }
    .screenshots-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(400px, 1fr)); gap: 16px; }
    .screenshot-card { border: 1px solid #e0e0e0; border-radius: 6px; overflow: hidden; }
    .screenshot-card img { width: 100%; display: block; }
    .screenshot-card .caption { padding: 8px 12px; font-size: 12px; color: #666; background: #fafafa; border-top: 1px solid #e0e0e0; font-family: monospace; }
    .footer { text-align: center; padding: 32px; color: #999; font-size: 12px; }
  </style>
</head>
<body>
  <div class="header">
    <h1>Playwright E2E Test Report</h1>
    <div class="subtitle">Loan Management Application &mdash; Generated ${new Date().toISOString().replace('T', ' ').substring(0, 19)} UTC</div>
  </div>
  <div class="stats">
    <div class="stat passed"><div class="value">${passedTests}</div><div class="label">Passed</div></div>
    <div class="stat failed"><div class="value">${failedTests}</div><div class="label">Failed</div></div>
    <div class="stat"><div class="value">${totalTests}</div><div class="label">Total</div></div>
    <div class="stat duration"><div class="value">${totalDuration}s</div><div class="label">Duration</div></div>
  </div>
  <div class="container">
${suites.map(suite => `
    <div class="suite">
      <div class="suite-header">
        <h2>${suite.name}</h2>
        <div class="file">${suite.file}</div>
      </div>
${suite.tests.map(test => `
      <div class="test">
        <div class="test-header">
          <span class="badge ${test.status}">${test.status}</span>
          <span class="test-title">${test.title}</span>
          <span class="test-duration">${test.duration}ms</span>
        </div>
${test.screenshots.length > 0 ? `
        <div class="screenshots">
          <div class="screenshots-grid">
${test.screenshots.map(s => `
            <div class="screenshot-card">
              <img src="./screenshots/${s}" alt="${s}" loading="lazy" />
              <div class="caption">${s}</div>
            </div>
`).join('')}
          </div>
        </div>
` : ''}
      </div>
`).join('')}
    </div>
`).join('')}
  </div>
  <div class="footer">
    Playwright HTML Report &mdash; ${totalTests} tests across ${suites.length} suites
  </div>
</body>
</html>`;

fs.writeFileSync(outputPath, html);
console.log('Report generated:', outputPath);
console.log('Screenshots referenced:', files.length);
