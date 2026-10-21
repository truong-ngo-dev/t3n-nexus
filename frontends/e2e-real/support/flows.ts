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

export interface FailedLogin { url: string; message: string; ms: number }

/**
 * Từ màn Đăng nhập của ứng dụng tới trang đăng nhập của Xác thực (một lần), rồi gửi email + mật khẩu `attempts` lần.
 * Mỗi lần trả địa chỉ sau chuyển hướng, thông báo hiện trên trang và thời gian từ lúc bấm tới lúc nhận trang lỗi (mili giây).
 * Chỉ dùng khi mong đợi đăng nhập thất bại.
 */
export async function failLogin(page: Page, email: string, password: string, attempts: number): Promise<FailedLogin[]> {
  await page.goto('/login');
  await page.getByTestId('login').click();
  const results: FailedLogin[] = [];
  for (let i = 0; i < attempts; i++) {
    await page.locator('#username').fill(email);
    await page.locator('#password').fill(password);
    const started = Date.now();
    await Promise.all([
      page.waitForResponse((r) => r.request().method() === 'GET' && /\/login\?(error|locked|unavailable)/.test(r.url())),
      page.getByRole('button', { name: 'Đăng nhập', exact: true }).click(),
    ]);
    const ms = Date.now() - started;
    await expect(page.locator('.alert')).toBeVisible();
    results.push({ url: page.url(), message: (await page.locator('.alert').first().innerText()).trim(), ms });
  }
  return results;
}
