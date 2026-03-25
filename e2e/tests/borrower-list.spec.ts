import { test, expect } from '@playwright/test';
import * as path from 'path';

const screenshotDir = path.join(__dirname, '..', 'test-results', 'screenshots');

test.describe('Borrower List Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/borrowers');
    await page.waitForSelector('table');
  });

  test('displays all test borrowers in the table', async ({ page }) => {
    const rows = page.locator('table tbody tr');
    await expect(rows).toHaveCount(2);

    await expect(page.getByText('T-B001')).toBeVisible();
    await expect(page.getByText('T-B002')).toBeVisible();
    await expect(page.getByText('Alice Test')).toBeVisible();
    await expect(page.getByText('Bob J. Sample')).toBeVisible();

    await page.screenshot({ path: path.join(screenshotDir, 'borrower-list-all-borrowers.png'), fullPage: true });
  });

  test('shows borrower details in columns', async ({ page }) => {
    await expect(page.getByText('alice.test@email.com')).toBeVisible();
    await expect(page.getByText('bob.sample@email.com')).toBeVisible();
    await expect(page.getByText('217-555-0001')).toBeVisible();
    await expect(page.getByText('512-555-0002')).toBeVisible();
    await expect(page.getByText('750')).toBeVisible();
    await expect(page.getByText('680')).toBeVisible();
    await expect(page.getByText('EMPLOYED')).toBeVisible();
    await expect(page.getByText('SELF-EMP')).toBeVisible();

    await page.screenshot({ path: path.join(screenshotDir, 'borrower-list-details.png'), fullPage: true });
  });

  test('navigates to borrower detail when clicking view icon', async ({ page }) => {
    await page.screenshot({ path: path.join(screenshotDir, 'borrower-list-before-nav.png'), fullPage: true });

    const viewButton = page.locator('a[mat-icon-button]').first();
    await viewButton.click();
    await expect(page).toHaveURL(/\/borrowers\/T-B001/);

    await page.screenshot({ path: path.join(screenshotDir, 'borrower-list-after-nav.png'), fullPage: true });
  });
});
