import { expect, test } from '@playwright/test';

// Chạy thẳng ng serve (không có BFF): phiên được giả bằng page.route. Đầu-cuối thật qua cổng ở bước 11.
test.describe('khung ứng dụng', () => {
  test('KT-19: địa chỉ lạ hiện trang không tìm thấy trong khung, giữ nguyên địa chỉ', async ({ page }) => {
    await page.route('**/bff/auth/session', (route) => route.fulfill({ status: 401, body: '' }));

    await page.goto('/khong-co-trang-nay');

    await expect(page.getByText('Không tìm thấy trang')).toBeVisible();
    await expect(page.locator('mat-toolbar')).toContainText('T3N Storefront');
    expect(new URL(page.url()).pathname).toBe('/khong-co-trang-nay');
  });

  test('đã đăng nhập: khung hiện nhãn vai, /account hiện mã tài khoản', async ({ page }) => {
    await page.route('**/bff/auth/session', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ userId: 'u-123', role: 'USER' }) }),
    );

    await page.goto('/account');

    await expect(page.getByTestId('role-label')).toHaveText('Người dùng');
    await expect(page.getByTestId('user-id')).toHaveText('u-123');
  });

  test('phiên lỗi 503: màn lỗi, không bị đưa đi đăng nhập; Thử lại thành công thì tới đúng trang đã mở', async ({ page }) => {
    let healthy = false;
    await page.route('**/bff/auth/session', (route) =>
      healthy
        ? route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ userId: 'u-123', role: 'USER' }) })
        : route.fulfill({ status: 503, body: '' }),
    );
    const navigations: string[] = [];
    page.on('framenavigated', (frame) => frame === page.mainFrame() && navigations.push(new URL(frame.url()).pathname));

    await page.goto('/account');
    await expect(page.getByTestId('session-error')).toBeVisible();
    expect(navigations.some((p) => p.startsWith('/bff'))).toBe(false);

    healthy = true;
    await page.getByTestId('retry').click();

    await expect(page.getByTestId('session-error')).toHaveCount(0);
    await expect(page.getByTestId('user-id')).toHaveText('u-123');
    expect(new URL(page.url()).pathname).toBe('/account');
  });
});

test.describe('màn Đăng ký', () => {
  const cors = { 'access-control-allow-origin': '*', 'access-control-allow-headers': '*', 'access-control-allow-methods': 'POST, OPTIONS' };

  async function mock(page: import('@playwright/test').Page, reply: { status: number; body: unknown }) {
    const bodies: unknown[] = [];
    await page.route('**/bff/auth/session', (route) => route.fulfill({ status: 401, body: '' }));
    await page.route('http://auth.localhost:8000/register', (route) => {
      if (route.request().method() === 'OPTIONS') return route.fulfill({ status: 204, headers: cors });
      bodies.push(route.request().postDataJSON());
      return route.fulfill({ status: reply.status, headers: { ...cors, 'content-type': 'application/json' }, body: JSON.stringify(reply.body) });
    });
    return bodies;
  }

  async function fillAndSubmit(page: import('@playwright/test').Page) {
    await page.goto('/register');
    await page.getByTestId('email').fill('a@b.vn');
    await page.getByTestId('password').fill('Passw0rd');
    await page.getByTestId('confirm').fill('Passw0rd');
    await page.getByTestId('submit').click();
  }

  test('đăng ký thành công: gửi đúng {email, password} và hiện đường tới Đăng nhập', async ({ page }) => {
    const bodies = await mock(page, { status: 201, body: { success: true, data: { userAccountId: 'x' } } });

    await fillAndSubmit(page);

    await expect(page.getByTestId('success')).toBeVisible();
    expect(bodies).toEqual([{ email: 'a@b.vn', password: 'Passw0rd' }]);
  });

  test('email đã có: hiện lý do, form còn nguyên để sửa', async ({ page }) => {
    await mock(page, { status: 409, body: { success: false, code: 'EMAIL_TAKEN', message: 'x' } });

    await fillAndSubmit(page);

    await expect(page.getByTestId('error')).toHaveText('Email đã được đăng ký');
    await expect(page.getByTestId('email')).toHaveValue('a@b.vn');
  });
});

test.describe('màn Đăng nhập', () => {
  test('KT-14: /login?error hiện thông báo chung và KHÔNG tự đi đăng nhập', async ({ page }) => {
    await page.route('**/bff/auth/session', (route) => route.fulfill({ status: 401, body: '' }));
    const loginRequests: string[] = [];
    await page.route('**/bff/auth/login', (route) => {
      loginRequests.push(route.request().url());
      return route.fulfill({ status: 200, contentType: 'text/html', body: 'xác thực giả' });
    });

    await page.goto('/login?error');
    await expect(page.getByTestId('login-error')).toBeVisible();
    await page.waitForTimeout(500); // chờ để một lần tự chuyển hướng (nếu có) kịp xảy ra

    expect(loginRequests).toEqual([]);
    expect(new URL(page.url()).pathname).toBe('/login');
  });

  test('bấm nút: điều hướng cấp cao tới /bff/auth/login', async ({ page }) => {
    await page.route('**/bff/auth/session', (route) => route.fulfill({ status: 401, body: '' }));
    await page.route('**/bff/auth/login', (route) => route.fulfill({ status: 200, contentType: 'text/html', body: 'xác thực giả' }));

    await page.goto('/login');
    await page.getByTestId('login').click();

    await expect(page).toHaveURL(/\/bff\/auth\/login$/);
  });

  test('khách vào /account: được đưa tới /bff/auth/login đúng một lần', async ({ page }) => {
    await page.route('**/bff/auth/session', (route) => route.fulfill({ status: 401, body: '' }));
    let hits = 0;
    await page.route('**/bff/auth/login', (route) => {
      hits++;
      return route.fulfill({ status: 200, contentType: 'text/html', body: 'xác thực giả' });
    });

    await page.goto('/account');

    await expect(page).toHaveURL(/\/bff\/auth\/login$/);
    expect(hits).toBe(1);
  });
});

test.describe('Đăng xuất', () => {
  const user = { status: 200, contentType: 'application/json', body: JSON.stringify({ userId: 'u-123', role: 'USER' }) };
  const AUTH_LOGOUT = 'http://auth.localhost:8000/connect/logout';

  async function signedIn(page: import('@playwright/test').Page) {
    await page.context().addCookies([{ name: 'XSRF-TOKEN', value: 'tok-1', url: 'http://localhost:4201' }]);
    await page.route('**/bff/auth/session', (route) => route.fulfill(user));
    await page.route(AUTH_LOGOUT, (route) => route.fulfill({ status: 200, contentType: 'text/html', body: '<p>auth logout</p>' }));
  }

  test('202: gửi kèm X-XSRF-TOKEN rồi điều hướng cấp cao tới Location', async ({ page }) => {
    await signedIn(page);
    const tokens: (string | undefined)[] = [];
    await page.route('**/bff/auth/logout', (route) => {
      tokens.push(route.request().headers()['x-xsrf-token']);
      return route.fulfill({ status: 202, headers: { location: AUTH_LOGOUT }, body: '' });
    });
    await page.goto('/');

    await page.getByTestId('logout').click();

    await expect(page).toHaveURL(AUTH_LOGOUT);
    expect(tokens).toEqual(['tok-1']);
  });

  test('403 CSRF: phục hồi bằng thăm dò phiên rồi gửi lại, người dùng vẫn đăng xuất được', async ({ page }) => {
    await signedIn(page);
    let attempts = 0;
    await page.route('**/bff/auth/logout', (route) => {
      attempts++;
      return attempts === 1
        ? route.fulfill({ status: 403, contentType: 'application/json', body: JSON.stringify({ success: false, code: 'CSRF_TOKEN_INVALID' }) })
        : route.fulfill({ status: 202, headers: { location: AUTH_LOGOUT }, body: '' });
    });
    await page.goto('/');

    await page.getByTestId('logout').click();

    await expect(page).toHaveURL(AUTH_LOGOUT);
    expect(attempts).toBe(2);
  });

  test('503: báo lỗi, vẫn đăng nhập, bấm lại được', async ({ page }) => {
    await signedIn(page);
    await page.route('**/bff/auth/logout', (route) => route.fulfill({ status: 503, body: '' }));
    await page.goto('/');

    await page.getByTestId('logout').click();

    await expect(page.getByTestId('logout-error')).toBeVisible();
    await expect(page.getByTestId('role-label')).toHaveText('Người dùng');
    await expect(page.getByTestId('logout')).toBeEnabled();
  });
});
