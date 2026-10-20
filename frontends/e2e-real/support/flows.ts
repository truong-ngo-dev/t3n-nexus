import { Page, expect } from '@playwright/test';
import { TEST_PASSWORD } from './infra';

export async function register(page: Page, email: string, password = TEST_PASSWORD): Promise<void> {
  await page.goto('/register');
  await page.getByTestId('email').fill(email);
  await page.getByTestId('password').fill(password);
  await page.getByTestId('confirm').fill(password);
  await page.getByTestId('submit').click();
}

/** Từ màn Đăng nhập của ứng dụng: bấm nút, nhập ở trang Xác thực, đợi về `/account` của cửa hàng. */
export async function signIn(page: Page, email: string, password = TEST_PASSWORD): Promise<void> {
  await page.goto('/login');
  await page.getByTestId('login').click();
  await page.locator('#username').fill(email);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page).toHaveURL('/account');
  // đợi ứng dụng khởi động xong (thăm dò phiên đầu có thể còn bay và cấp lại cookie XSRF-TOKEN)
  await expect(page.getByTestId('role-label')).toBeVisible();
}

export async function expectGuest(page: Page): Promise<void> {
  await expect(page.getByTestId('role-label')).toHaveCount(0);
  await expect(page.locator('mat-toolbar a[href="/login"]')).toBeVisible();
}
