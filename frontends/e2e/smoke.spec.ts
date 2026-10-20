import { expect, test } from '@playwright/test';

test('the app loads and renders the root component', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('app-root')).toBeAttached();
  await expect(page).toHaveTitle(/T3N/);
});
