import { expect, test } from '@playwright/test';
import { expectGuest, register, signIn } from './support/flows';
import { ADMIN_EMAIL, TEST_PASSWORD, accountCount, bffSessionKeys, redisDelete, uniqueEmail } from './support/infra';

const AUTH = 'http://auth.localhost:8000';

test.describe('luồng người dùng thật', () => {
  test('KT-09 (S1): đăng ký, đăng nhập, thấy mã và vai, đăng xuất, /account đòi đăng nhập, đăng nhập lại về /account', async ({ page }) => {
    const email = uniqueEmail();

    await register(page, email);
    await expect(page.getByTestId('success')).toBeVisible();
    await signIn(page, email);

    await expect(page.getByTestId('role-label')).toHaveText('Người dùng');
    await expect(page.getByTestId('role')).toHaveText('Người dùng');
    await expect(page.getByTestId('user-id')).toHaveText(/^[0-9a-f-]{36}$/);

    await page.getByTestId('logout').click();
    await expect(page).toHaveURL('/');
    await expectGuest(page);

    await page.goto('/account');
    await expect(page.locator('#username')).toBeVisible(); // trang đăng nhập của Xác thực
    await page.locator('#username').fill(email);
    await page.locator('#password').fill(TEST_PASSWORD);
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    await expect(page).toHaveURL('/account');
    await expect(page.getByTestId('role-label')).toHaveText('Người dùng');
  });

  test('KT-10 (S2): email trùng khác chữ hoa, email miền nội bộ, bấm đăng ký hai lần chỉ một tài khoản', async ({ page }) => {
    const email = uniqueEmail();
    await register(page, email);
    await expect(page.getByTestId('success')).toBeVisible();

    await register(page, email.toUpperCase());
    await expect(page.getByTestId('error')).toHaveText('Email đã được đăng ký');

    await register(page, 'e2e-noibo@t3nexus.com.vn');
    await expect(page.getByTestId('error')).toHaveText('Email không dùng được để đăng ký');
    expect(accountCount('e2e-noibo@t3nexus.com.vn')).toBe(0);

    const twice = uniqueEmail();
    await page.goto('/register');
    await page.getByTestId('email').fill(twice);
    await page.getByTestId('password').fill(TEST_PASSWORD);
    await page.getByTestId('confirm').fill(TEST_PASSWORD);
    await page.getByTestId('submit').dblclick();
    await expect(page.getByTestId('success')).toBeVisible();
    expect(accountCount(twice)).toBe(1);
  });

  test('KT-11 (S4): Quản trị có sẵn hiện "Quản trị"; tự đăng ký (kể cả gửi kèm role ADMIN) hiện "Người dùng"', async ({ page, request }) => {
    await signIn(page, ADMIN_EMAIL);
    await expect(page.getByTestId('role-label')).toHaveText('Quản trị');
    await page.getByTestId('logout').click();
    await expect(page).toHaveURL('/');

    const sneaky = uniqueEmail();
    const res = await request.post(`${AUTH}/register`, { data: { email: sneaky, password: TEST_PASSWORD, role: 'ADMIN' } });
    expect([201, 400]).toContain(res.status()); // bị từ chối hoặc bỏ qua trường lạ; cả hai đều không được thành Quản trị
    if (res.status() === 201) {
      await signIn(page, sneaky);
      await expect(page.getByTestId('role-label')).toHaveText('Người dùng');
    }
  });

  test('KT-12 (S5): xóa phiên BFF trước rồi đăng xuất vẫn về trạng thái chưa đăng nhập', async ({ page }) => {
    const email = uniqueEmail();
    await register(page, email);
    await signIn(page, email);

    expect(bffSessionKeys().length).toBeGreaterThan(0); // có phiên BFF thật để xóa
    redisDelete(bffSessionKeys());
    await page.getByTestId('logout').click();

    await expect(page).toHaveURL('/');
    await expectGuest(page);
  });

  test('KT-13: xóa cookie XSRF-TOKEN rồi đăng xuất vẫn thành công', async ({ page, context }) => {
    const email = uniqueEmail();
    await register(page, email);
    await signIn(page, email);

    expect((await context.cookies()).map((c) => c.name)).toContain('XSRF-TOKEN');
    await context.clearCookies({ name: 'XSRF-TOKEN' });
    expect((await context.cookies()).map((c) => c.name)).not.toContain('XSRF-TOKEN');
    await page.getByTestId('logout').click();

    await expect(page).toHaveURL('/');
    await expectGuest(page);
  });

  test('KT-14: /login?error qua cổng hiện thông báo chung, không tự chuyển hướng', async ({ page }) => {
    await page.goto('/login?error');

    await expect(page.getByTestId('login-error')).toBeVisible();
    await page.waitForTimeout(1500);
    expect(new URL(page.url()).pathname).toBe('/login');
  });

  test('KT-16 (một phần): xóa phiên BFF khi đang dùng rồi tải lại: đi đăng nhập, SSO còn nên không hỏi mật khẩu', async ({ page }) => {
    const email = uniqueEmail();
    await register(page, email);
    await signIn(page, email);

    expect(bffSessionKeys().length).toBeGreaterThan(0);
    redisDelete(bffSessionKeys());
    await page.reload();

    await expect(page).toHaveURL('/account');
    await expect(page.getByTestId('role-label')).toHaveText('Người dùng');
  });

  test('KT-17: JS thấy XSRF-TOKEN, không thấy STOREFRONT_SESSION; localStorage không có token', async ({ page, context }) => {
    const email = uniqueEmail();
    await register(page, email);
    await signIn(page, email);

    const cookieNames = await page.evaluate(() => document.cookie.split(';').map((c) => c.trim().split('=')[0]));
    expect(cookieNames).toContain('XSRF-TOKEN');
    expect(cookieNames).not.toContain('STOREFRONT_SESSION');

    const session = (await context.cookies()).find((c) => c.name === 'STOREFRONT_SESSION');
    expect(session?.httpOnly).toBe(true);

    expect(await page.evaluate(() => localStorage.length)).toBe(0);
    const sessionKeys = await page.evaluate(() => Object.keys(sessionStorage));
    expect(sessionKeys.filter((k) => k !== 'justSignedIn')).toEqual([]);
  });

  test('KT-20: thăm dò phiên 503 qua cổng: màn lỗi, không coi là khách; hồi phục thì Thử lại vào được', async ({ page }) => {
    await page.route('**/bff/auth/session', (route) => route.fulfill({ status: 503, body: '' }));
    await page.goto('/');
    await expect(page.getByTestId('session-error')).toBeVisible();
    await expect(page.locator('mat-toolbar a[href="/login"]')).toHaveCount(0);

    await page.unroute('**/bff/auth/session');
    await page.getByTestId('retry').click();

    await expect(page.getByTestId('session-error')).toHaveCount(0);
    await expect(page.locator('mat-toolbar a[href="/login"]')).toBeVisible();
  });
});
