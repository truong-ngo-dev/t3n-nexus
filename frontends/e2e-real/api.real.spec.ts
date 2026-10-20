import { Page, expect, test } from '@playwright/test';
import { createServer } from 'node:http';
import { register, signIn } from './support/flows';
import { bffSessionKeys, redisDelete, uniqueEmail } from './support/infra';

// Chưa trang nào của C0 gọi /api/**, nên gọi bằng đúng HttpClient của ứng dụng (đủ chuỗi bộ chặn) lấy qua công cụ gỡ lỗi
// của Angular ở bản dev (`ng.getComponent`). Dựa vào tên trường `sessionService.http`: đổi tên thì ca này vỡ, không vô hình.
const API = '/api/customer/ping';
const CUSTOMER_SERVICE_PORT = 8002; // storefront.routes.customer-service.uri của BFF

async function callApi(page: Page): Promise<{ ok: boolean; status?: number }> {
  return page.evaluate(
    (url) =>
      new Promise<{ ok: boolean; status?: number }>((resolve) => {
        const ng = (window as unknown as { ng: { getComponent(el: Element): { sessionService: { http: { get(u: string): { subscribe(o: object): void } } } } } }).ng;
        const http = ng.getComponent(document.querySelector('app-root') as Element).sessionService.http;
        http.get(url).subscribe({ next: () => resolve({ ok: true }), error: (e: { status: number }) => resolve({ ok: false, status: e.status }) });
      }),
    API,
  );
}

const navigationsTo = (page: Page, test: (url: string) => boolean): string[] => {
  const seen: string[] = [];
  page.on('request', (r) => r.isNavigationRequest() && test(r.url()) && seen.push(r.url()));
  return seen;
};
const loginNavigations = (page: Page) => navigationsTo(page, (u) => u.endsWith('/bff/auth/login'));

test.describe('401 từ API qua BFF thật', () => {
  test('KT-16: xóa phiên BFF khi đang dùng rồi gọi API: đi đăng nhập một lần, SSO còn nên không hỏi mật khẩu', async ({ page }) => {
    const email = uniqueEmail();
    await register(page, email);
    await signIn(page, email);
    const loginHits = loginNavigations(page);
    const passwordPrompts = navigationsTo(page, (u) => u.startsWith('http://auth.localhost:8000/login')); // trang nhập mật khẩu của Xác thực

    expect(bffSessionKeys().length).toBeGreaterThan(0);
    redisDelete(bffSessionKeys());
    const result = await callApi(page).catch(() => ({ ok: false, status: -1 })); // trang bị điều hướng đi giữa chừng

    await expect(page).toHaveURL('/account');
    await expect(page.getByTestId('role-label')).toHaveText('Người dùng');
    expect(loginHits).toHaveLength(1);
    expect(passwordPrompts).toEqual([]);
    expect(result.ok).toBe(false);
  });

  test('KT-15: dịch vụ sau từ chối token ngay sau đăng nhập: dừng ở lần 401 thứ hai, không vòng lặp', async ({ page }) => {
    const hits: string[] = [];
    const downstream = createServer((req, res) => {
      hits.push(`${req.method} ${req.url}`);
      res.writeHead(401, { 'content-type': 'application/json' }).end(JSON.stringify({ success: false, code: 'UNAUTHENTICATED' }));
    });
    await new Promise<void>((ok, bad) => downstream.once('error', bad).listen(CUSTOMER_SERVICE_PORT, '127.0.0.1', ok));
    try {
      const email = uniqueEmail();
      await register(page, email);
      await signIn(page, email);
      const loginHits = loginNavigations(page);

      await callApi(page).catch(() => undefined); // 401 lần đầu: bộ chặn đi đăng nhập (trang điều hướng đi)
      await expect(page).toHaveURL('/account');
      await expect(page.getByTestId('role-label')).toBeVisible();
      expect(loginHits).toHaveLength(1);

      const second = await callApi(page); // 401 ngay sau khi quay về: phải dừng
      expect(second).toEqual({ ok: false, status: 401 });
      await page.waitForTimeout(1500);
      expect(new URL(page.url()).pathname).toBe('/account');
      expect(loginHits).toHaveLength(1);
      expect(hits.length).toBeGreaterThanOrEqual(2); // dịch vụ giả thực sự bị gọi
    } finally {
      await new Promise<void>((done) => downstream.close(() => done()));
    }
  });
});
