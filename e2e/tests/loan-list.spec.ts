import { test, expect } from '@playwright/test';

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
  });

  test('shows correct financial data and status', async ({ page }) => {
    // Original amounts
    await expect(page.getByText('$250,000.00')).toBeVisible();
    await expect(page.getByText('$180,000.00')).toBeVisible();

    // Interest rates
    await expect(page.getByText('4.500%')).toBeVisible();
    await expect(page.getByText('5.750%')).toBeVisible();

    // Status chips
    const activeChips = page.locator('mat-chip');
    await expect(activeChips).toHaveCount(2);
    await expect(activeChips.first()).toContainText('Active');
  });

  test('navigates to loan detail when clicking view icon', async ({ page }) => {
    const viewButton = page.locator('a[mat-icon-button]').first();
    await viewButton.click();
    await expect(page).toHaveURL(/\/loans\/TLN-001/);
  });
});
