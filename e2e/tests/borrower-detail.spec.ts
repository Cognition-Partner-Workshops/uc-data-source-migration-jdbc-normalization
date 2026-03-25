import { test, expect } from '@playwright/test';

test.describe('Borrower Detail Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/borrowers/T-B001');
    await page.waitForSelector('.detail-header');
  });

  test('displays contact information card', async ({ page }) => {
    // Header
    await expect(page.locator('h2')).toContainText('Alice Test');
    await expect(page.locator('.borrower-id')).toContainText('T-B001');

    // Contact card
    const contact = page.locator('mat-card').filter({ hasText: 'Contact Information' });
    await expect(contact.getByText('alice.test@email.com')).toBeVisible();
    await expect(contact.getByText('217-555-0001')).toBeVisible();
    await expect(contact.getByText('Springfield')).toBeVisible();
    await expect(contact.getByText('IL', { exact: true })).toBeVisible();
  });

  test('displays financial profile card', async ({ page }) => {
    const financial = page.locator('mat-card').filter({ hasText: 'Financial Profile' });
    await expect(financial.getByText('750')).toBeVisible();
    await expect(financial.getByText('EMPLOYED')).toBeVisible();
  });

  test('displays associated loans table', async ({ page }) => {
    const loansCard = page.locator('mat-card').filter({ hasText: 'Loans' });
    await loansCard.waitFor();

    const rows = loansCard.locator('table tbody tr');
    await expect(rows).toHaveCount(1);

    await expect(loansCard.getByText('TLN-001')).toBeVisible();
    await expect(loansCard.getByText('Test 30-Year Fixed')).toBeVisible();
    await expect(loansCard.getByText('$250,000.00')).toBeVisible();
    await expect(loansCard.getByText('$230,000.00')).toBeVisible();
    await expect(loansCard.getByText('4.500%')).toBeVisible();
    await expect(loansCard.locator('mat-chip')).toContainText('Active');
  });
});
