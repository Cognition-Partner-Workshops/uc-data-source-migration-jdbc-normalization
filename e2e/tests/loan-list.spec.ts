import { test, expect } from '@playwright/test';
import * as path from 'path';

const screenshotDir = path.join(__dirname, '..', 'test-results', 'screenshots');

test.describe('Loan List Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/loans');
    await page.waitForSelector('table');
  });

  test('displays all test loans in the table', async ({ page }) => {
    const rows = page.locator('table tbody tr');
    await expect(rows).toHaveCount(2);

    await expect(page.getByText('TLN-001')).toBeVisible();
    await expect(page.getByText('TLN-002')).toBeVisible();
    await expect(page.getByText('Alice Test')).toBeVisible();
    await expect(page.getByText('Bob Sample')).toBeVisible();

    await page.screenshot({ path: path.join(screenshotDir, 'loan-list-all-loans.png'), fullPage: true });
  });

  test('shows correct financial data and status', async ({ page }) => {
    await expect(page.getByText('$250,000.00')).toBeVisible();
    await expect(page.getByText('$180,000.00')).toBeVisible();
    await expect(page.getByText('4.500%')).toBeVisible();
    await expect(page.getByText('5.750%')).toBeVisible();

    const activeChips = page.locator('mat-chip');
    await expect(activeChips).toHaveCount(2);
    await expect(activeChips.first()).toContainText('Active');

    await page.screenshot({ path: path.join(screenshotDir, 'loan-list-financial-data.png'), fullPage: true });
  });

  test('navigates to loan detail when clicking view icon', async ({ page }) => {
    await page.screenshot({ path: path.join(screenshotDir, 'loan-list-before-nav.png'), fullPage: true });

    const viewButton = page.locator('a[mat-icon-button]').first();
    await viewButton.click();
    await expect(page).toHaveURL(/\/loans\/TLN-001/);

    await page.screenshot({ path: path.join(screenshotDir, 'loan-list-after-nav.png'), fullPage: true });
  });
});
