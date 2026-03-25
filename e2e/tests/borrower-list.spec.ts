import { test, expect } from '@playwright/test';

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
  });

  test('shows borrower details in columns', async ({ page }) => {
    // Emails
    await expect(page.getByText('alice.test@email.com')).toBeVisible();
    await expect(page.getByText('bob.sample@email.com')).toBeVisible();

    // Phones
    await expect(page.getByText('217-555-0001')).toBeVisible();
    await expect(page.getByText('512-555-0002')).toBeVisible();

    // Credit scores
    await expect(page.getByText('750')).toBeVisible();
    await expect(page.getByText('680')).toBeVisible();

    // Employment status
    await expect(page.getByText('EMPLOYED')).toBeVisible();
    await expect(page.getByText('SELF-EMP')).toBeVisible();
  });

  test('navigates to borrower detail when clicking view icon', async ({ page }) => {
    const viewButton = page.locator('a[mat-icon-button]').first();
    await viewButton.click();
    await expect(page).toHaveURL(/\/borrowers\/T-B001/);
  });
});
