import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: '.',
  testMatch: '**/*.spec.ts',
  testIgnore: ['**/output/**', '**/dist/**', '**/test-results/**'],
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI
    ? [
        ['list'],
        ['junit', { outputFile: 'output/playwright/junit.xml' }],
        ['html', { outputFolder: 'output/playwright/report', open: 'never' }],
      ]
    : [['list'], ['html', { outputFolder: 'output/playwright/report', open: 'never' }]],
  outputDir: 'output/playwright/results',
  use: {
    baseURL: 'http://localhost:5175',
    trace: 'on-first-retry',
  },
  webServer: {
    command: 'node node_modules/vite/bin/vite.js --host localhost --port 5175 --strictPort',
    url: 'http://localhost:5175',
    reuseExistingServer: false,
    timeout: 120000,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
