import { test, expect } from '@playwright/test';
import * as path from 'path';

const screenshotDir = path.join(__dirname, '..', 'test-results', 'screenshots');

test.describe('Loan Detail Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/loans/TLN-001');
    await page.waitForSelector('.detail-header');
  });

  test('displays loan summary card with correct data', async ({ page }) => {
    await expect(page.locator('h2')).toContainText('TLN-001');

    const summary = page.locator('mat-card').filter({ hasText: 'Loan Summary' });
    await expect(summary.getByText('Alice Test')).toBeVisible();
    await expect(summary.getByText('Test 30-Year Fixed')).toBeVisible();
    await expect(summary.getByText('$250,000.00')).toBeVisible();
    await expect(summary.getByText('$230,000.00')).toBeVisible();
    await expect(summary.getByText('4.500%')).toBeVisible();

    await page.screenshot({ path: path.join(screenshotDir, 'loan-detail-summary.png'), fullPage: true });
  });

  test('displays property information', async ({ page }) => {
    const property = page.locator('mat-card').filter({ hasText: 'Property' });
    await expect(property.getByText('100 Main St, Springfield, IL 60001')).toBeVisible();
    await expect(property.getByText('Single Family Residence')).toBeVisible();

    await page.screenshot({ path: path.join(screenshotDir, 'loan-detail-property.png'), fullPage: true });
  });

  test('displays payment history table with correct rows', async ({ page }) => {
    const paymentsCard = page.locator('mat-card').filter({ hasText: 'Payment History' });
    await paymentsCard.waitFor();

    const rows = paymentsCard.locator('table tbody tr');
    await expect(rows).toHaveCount(2);

    await expect(paymentsCard.getByText('12/01/2025')).toBeVisible();
    await expect(paymentsCard.getByText('11/01/2025')).toBeVisible();

    const amounts = paymentsCard.getByText('$1,302.50');
    await expect(amounts.first()).toBeVisible();

    const postedChips = paymentsCard.locator('mat-chip');
    await expect(postedChips).toHaveCount(2);
    await expect(postedChips.first()).toContainText('Posted');

    await page.screenshot({ path: path.join(screenshotDir, 'loan-detail-payments.png'), fullPage: true });
  });
});
