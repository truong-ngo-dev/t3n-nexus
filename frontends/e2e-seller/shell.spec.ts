import { expect, test, type Page } from '@playwright/test';

/*
 * KT-05 (flow.md mục 6): mỗi trạng thái ở mục 2 ra đúng màn hình. Chạy thẳng `ng serve` (không có BFF):
 * phiên và hồ sơ được giả bằng `page.route`. Chạy thật qua cổng ở bước 9.
 */
const SESSION = '**/bff/auth/session';
const PROFILE = '**/api/seller/seller/profile';
const LOGIN = '**/bff/auth/login';
const LOGOUT = '**/bff/auth/logout';
const AUTH_LOGOUT = 'http://auth.localhost:8000/connect/logout';

type Role = 'USER' | 'ADMIN';
type ProfileStatus = 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED';

const json = (status: number, body: unknown) => ({ status, contentType: 'application/json', body: JSON.stringify(body) });

async function mockSession(page: Page, reply: { role: Role } | 'guest' | 'down') {
  await page.route(SESSION, (route) => {
    if (reply === 'guest') return route.fulfill({ status: 401, body: '' });
    if (reply === 'down') return route.fulfill({ status: 503, body: '' });
    return route.fulfill(json(200, { userId: 'u-1', role: reply.role }));
  });
}

type ProfileReply = { status: ProfileStatus; reason?: string } | 'none' | 'down';

async function mockProfile(page: Page, reply: ProfileReply) {
  const requests: string[] = [];
  await page.route(PROFILE, (route) => {
    requests.push(route.request().url());
    if (reply === 'none') return route.fulfill(json(404, { success: false, code: 'SELLER_PROFILE_NOT_FOUND', message: 'x' }));
    if (reply === 'down') return route.fulfill({ status: 500, body: '' });
    return route.fulfill(
      json(200, { success: true, data: { id: 's-1', status: reply.status, sellerType: 'INDIVIDUAL', legalInfo: {}, shop: {}, rejectionReason: reply.reason ?? null } }),
    );
  });
  return requests;
}

const pathOf = (page: Page) => new URL(page.url()).pathname;

test.describe('khách', () => {
  test('mở "/": được đưa tới /bff/auth/login đúng một lần', async ({ page }) => {
    await mockSession(page, 'guest');
    let hits = 0;
    await page.route(LOGIN, (route) => {
      hits++;
      return route.fulfill({ status: 200, contentType: 'text/html', body: 'xác thực giả' });
    });

    await page.goto('/');

    await expect(page).toHaveURL(/\/bff\/auth\/login$/);
    expect(hits).toBe(1);
  });

  test('"/login": thấy nút đăng nhập, header không có nhãn trạng thái và nút đăng xuất', async ({ page }) => {
    await mockSession(page, 'guest');

    await page.goto('/login');

    await expect(page.getByTestId('login')).toHaveText('Đăng nhập');
    await expect(page.getByTestId('login-error')).toHaveCount(0);
    await expect(page.getByTestId('status-label')).toHaveCount(0);
    await expect(page.getByTestId('logout')).toHaveCount(0);
    await expect(page.getByTestId('checking')).toHaveCount(0); // hồ sơ của khách không bao giờ nạp, không được kẹt ở màn chờ
  });

  test('"/login?error": hiện thông báo, đổi chữ nút, KHÔNG tự đi đăng nhập', async ({ page }) => {
    await mockSession(page, 'guest');
    const loginRequests: string[] = [];
    await page.route(LOGIN, (route) => {
      loginRequests.push(route.request().url());
      return route.fulfill({ status: 200, contentType: 'text/html', body: 'xác thực giả' });
    });

    await page.goto('/login?error');
    await expect(page.getByTestId('login-error')).toBeVisible();
    await expect(page.getByTestId('login')).toHaveText('Thử lại');
    await page.waitForTimeout(500); // chờ để một lần tự chuyển hướng (nếu có) kịp xảy ra

    expect(loginRequests).toEqual([]);
    expect(pathOf(page)).toBe('/login');
  });

  test('bấm nút: điều hướng cấp cao tới /bff/auth/login', async ({ page }) => {
    await mockSession(page, 'guest');
    await page.route(LOGIN, (route) => route.fulfill({ status: 200, contentType: 'text/html', body: 'xác thực giả' }));

    await page.goto('/login');
    await page.getByTestId('login').click();

    await expect(page).toHaveURL(/\/bff\/auth\/login$/);
  });
});

test.describe('USER theo trạng thái hồ sơ', () => {
  const cases: { name: string; reply: ProfileReply; screen: string; label: string }[] = [
    { name: 'chưa là người bán (404)', reply: 'none', screen: 'home-none', label: 'Chưa là người bán' },
    { name: 'chờ duyệt', reply: { status: 'PENDING_REVIEW' }, screen: 'home-pending', label: 'Chờ duyệt' },
    { name: 'đã duyệt', reply: { status: 'APPROVED' }, screen: 'home-approved', label: 'Đã duyệt' },
    { name: 'bị từ chối', reply: { status: 'REJECTED', reason: 'Ảnh giấy tờ mờ' }, screen: 'home-rejected', label: 'Bị từ chối' },
  ];

  for (const c of cases) {
    test(`${c.name}: hiện đúng màn và nhãn ở header`, async ({ page }) => {
      await mockSession(page, { role: 'USER' });
      await mockProfile(page, c.reply);

      await page.goto('/');

      await expect(page.getByTestId(c.screen)).toBeVisible();
      await expect(page.getByTestId('status-label')).toHaveText(c.label);
      await expect(page.getByTestId('logout')).toBeVisible();
    });
  }

  test('bị từ chối: hiện lý do máy chủ trả về', async ({ page }) => {
    await mockSession(page, { role: 'USER' });
    await mockProfile(page, { status: 'REJECTED', reason: 'Ảnh giấy tờ mờ' });

    await page.goto('/');

    await expect(page.getByTestId('reject-reason')).toHaveText('Ảnh giấy tờ mờ');
  });

  test('đã đăng nhập mở "/login": về "/"', async ({ page }) => {
    await mockSession(page, { role: 'USER' });
    await mockProfile(page, { status: 'APPROVED' });

    await page.goto('/login');

    await expect(page.getByTestId('home-approved')).toBeVisible();
    expect(pathOf(page)).toBe('/');
  });
});

test.describe('ADMIN', () => {
  test('thấy trang thông báo, không nạp hồ sơ, không có nhãn, vẫn đăng xuất được', async ({ page }) => {
    await mockSession(page, { role: 'ADMIN' });
    const profileRequests = await mockProfile(page, { status: 'APPROVED' });

    await page.goto('/');

    await expect(page.getByTestId('forbidden-account')).toBeVisible();
    expect(pathOf(page)).toBe('/forbidden-account');
    await expect(page.getByTestId('status-label')).toHaveCount(0);
    await expect(page.getByTestId('logout')).toBeVisible();
    await expect(page.getByTestId('checking')).toHaveCount(0);
    expect(profileRequests).toEqual([]);
  });
});

test.describe('lỗi và thử lại', () => {
  test('phiên 503: màn lỗi, không bị đưa đi đăng nhập; Thử lại thành công thì vào được trang đã mở', async ({ page }) => {
    let healthy = false;
    await page.route(SESSION, (route) => (healthy ? route.fulfill(json(200, { userId: 'u-1', role: 'USER' })) : route.fulfill({ status: 503, body: '' })));
    await mockProfile(page, { status: 'APPROVED' });
    const navigations: string[] = [];
    page.on('framenavigated', (frame) => frame === page.mainFrame() && navigations.push(new URL(frame.url()).pathname));

    await page.goto('/');
    await expect(page.getByTestId('screen-error')).toBeVisible();
    expect(navigations.some((p) => p.startsWith('/bff'))).toBe(false);

    healthy = true;
    await page.getByTestId('retry').click();

    // Điểm cần chứng minh: lần điều hướng đầu bị guard hủy, nên thử lại xong phải tự vào lại trang (không để vùng nội dung trống).
    await expect(page.getByTestId('screen-error')).toHaveCount(0);
    await expect(page.getByTestId('home-approved')).toBeVisible();
    expect(pathOf(page)).toBe('/');
  });

  test('hồ sơ 500: màn lỗi (không coi là chưa có hồ sơ); Thử lại thành công thì hiện đúng màn', async ({ page }) => {
    await mockSession(page, { role: 'USER' });
    let healthy = false;
    await page.route(PROFILE, (route) =>
      healthy
        ? route.fulfill(json(200, { success: true, data: { id: 's-1', status: 'PENDING_REVIEW', sellerType: 'INDIVIDUAL', legalInfo: {}, shop: {} } }))
        : route.fulfill({ status: 500, body: '' }),
    );

    await page.goto('/');
    await expect(page.getByTestId('screen-error')).toBeVisible();
    await expect(page.getByTestId('home-none')).toHaveCount(0);

    healthy = true;
    await page.getByTestId('retry').click();

    await expect(page.getByTestId('home-pending')).toBeVisible();
    await expect(page.getByTestId('screen-error')).toHaveCount(0);
  });

  test('bấm Thử lại nhiều lần liền: chỉ gửi một lượt thăm dò (bấm đúp)', async ({ page }) => {
    let sessionHits = 0;
    let healthy = false;
    await page.route(SESSION, async (route) => {
      sessionHits++;
      if (!healthy) return route.fulfill({ status: 503, body: '' });
      await new Promise((r) => setTimeout(r, 300)); // giữ lượt thử lại "đang chờ" đủ lâu để bấm lần hai
      return route.fulfill(json(200, { userId: 'u-1', role: 'USER' }));
    });
    await mockProfile(page, { status: 'APPROVED' });

    await page.goto('/');
    await expect(page.getByTestId('screen-error')).toBeVisible();
    const before = sessionHits;

    healthy = true;
    await page.getByTestId('retry').dblclick();

    await expect(page.getByTestId('home-approved')).toBeVisible();
    expect(sessionHits - before).toBe(1);
  });
});

test.describe('đăng xuất', () => {
  async function signedIn(page: Page) {
    await page.context().addCookies([{ name: 'XSRF-TOKEN', value: 'tok-1', url: 'http://localhost:4202' }]);
    await mockSession(page, { role: 'USER' });
    await mockProfile(page, { status: 'APPROVED' });
    await page.route(AUTH_LOGOUT, (route) => route.fulfill({ status: 200, contentType: 'text/html', body: '<p>auth logout</p>' }));
  }

  test('202: gửi kèm X-XSRF-TOKEN rồi điều hướng cấp cao tới Location', async ({ page }) => {
    await signedIn(page);
    const tokens: (string | undefined)[] = [];
    await page.route(LOGOUT, (route) => {
      tokens.push(route.request().headers()['x-xsrf-token']);
      return route.fulfill({ status: 202, headers: { location: AUTH_LOGOUT }, body: '' });
    });
    await page.goto('/');

    await page.getByTestId('logout').click();

    await expect(page).toHaveURL(AUTH_LOGOUT);
    expect(tokens).toEqual(['tok-1']);
  });

  test('bấm đúp: chỉ gửi một yêu cầu đăng xuất', async ({ page }) => {
    await signedIn(page);
    let hits = 0;
    await page.route(LOGOUT, async (route) => {
      hits++;
      await new Promise((r) => setTimeout(r, 300)); // giữ yêu cầu "đang chờ" để nút còn bị khóa lúc bấm lần hai
      return route.fulfill({ status: 202, headers: { location: AUTH_LOGOUT }, body: '' });
    });
    await page.goto('/');

    await page.getByTestId('logout').dblclick();

    await expect(page).toHaveURL(AUTH_LOGOUT);
    expect(hits).toBe(1);
  });

  test('503: báo lỗi, vẫn đăng nhập, nút mở lại để bấm lại', async ({ page }) => {
    await signedIn(page);
    await page.route(LOGOUT, (route) => route.fulfill({ status: 503, body: '' }));
    await page.goto('/');

    await page.getByTestId('logout').click();

    await expect(page.getByTestId('logout-error')).toBeVisible();
    await expect(page.getByTestId('status-label')).toHaveText('Đã duyệt');
    await expect(page.getByTestId('logout')).toBeEnabled();
  });
});

test('địa chỉ lạ: hiện trang không tìm thấy, giữ nguyên địa chỉ', async ({ page }) => {
  await mockSession(page, 'guest');

  await page.goto('/khong-co-trang-nay');

  await expect(page.getByTestId('not-found')).toBeVisible();
  expect(pathOf(page)).toBe('/khong-co-trang-nay');
});
