import { test, expect } from '@playwright/test';

test.describe('Loan Detail Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/loans/TLN-001');
    await page.waitForSelector('.detail-header');
  });

  test('displays loan summary card with correct data', async ({ page }) => {
    // Header
    await expect(page.locator('h2')).toContainText('TLN-001');

    // Summary card fields
    const summary = page.locator('mat-card').filter({ hasText: 'Loan Summary' });
    await expect(summary.getByText('Alice Test')).toBeVisible();
    await expect(summary.getByText('Test 30-Year Fixed')).toBeVisible();
    await expect(summary.getByText('$250,000.00')).toBeVisible();
    await expect(summary.getByText('$230,000.00')).toBeVisible();
    await expect(summary.getByText('4.500%')).toBeVisible();
  });

  test('displays property information', async ({ page }) => {
    const property = page.locator('mat-card').filter({ hasText: 'Property' });
    await expect(property.getByText('100 Main St, Springfield, IL 60001')).toBeVisible();
    await expect(property.getByText('Single Family Residence')).toBeVisible();
  });

  test('displays payment history table with correct rows', async ({ page }) => {
    const paymentsCard = page.locator('mat-card').filter({ hasText: 'Payment History' });
    await paymentsCard.waitFor();

    const rows = paymentsCard.locator('table tbody tr');
    await expect(rows).toHaveCount(2);

    // First payment (Dec 2025)
    await expect(paymentsCard.getByText('12/01/2025')).toBeVisible();
    // Second payment (Nov 2025)
    await expect(paymentsCard.getByText('11/01/2025')).toBeVisible();

    // Total amounts
    const amounts = paymentsCard.getByText('$1,302.50');
    await expect(amounts.first()).toBeVisible();

    // Status
    const postedChips = paymentsCard.locator('mat-chip');
    await expect(postedChips).toHaveCount(2);
    await expect(postedChips.first()).toContainText('Posted');
  });
});
