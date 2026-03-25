import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  forbidOnly: true,
  retries: 0,
  workers: 1,
  reporter: [
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
    ['json', { outputFile: 'test-results/results.json' }],
  ],
  outputDir: 'playwright-report/artifacts',
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'on-first-retry',
    screenshot: 'on',
  },
  projects: [
    {
      name: 'chromium',
      use: { browserName: 'chromium' },
    },
  ],
  webServer: [
    {
      command: 'npm run dev:test',
      cwd: '../server',
      port: 3000,
      reuseExistingServer: false,
      timeout: 30_000,
    },
    {
      command: 'npx ng serve --port 4200',
      cwd: '../client',
      port: 4200,
      reuseExistingServer: false,
      timeout: 60_000,
    },
  ],
});
