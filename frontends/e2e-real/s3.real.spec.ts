import { APIRequestContext, expect, test } from '@playwright/test';
import { failLogin } from './support/flows';
import { TEST_PASSWORD, uniqueEmail } from './support/infra';

const AUTH = 'http://auth.localhost:8000';

/** Đăng ký thẳng qua cổng (host auth), không dựng giao diện: giữ số yêu cầu qua cổng thấp. */
async function registerByApi(request: APIRequestContext, email: string): Promise<void> {
  const res = await request.post(`${AUTH}/register`, { data: { email, password: TEST_PASSWORD } });
  expect(res.status(), `đăng ký ${email}`).toBe(201);
}

const median = (xs: number[]): number => [...xs].sort((a, b) => a - b)[Math.floor(xs.length / 2)];

test.describe('S3: sai email và sai mật khẩu cho cùng một kết quả (INV-ATH-03)', () => {
  test('KT-21 (S3, nhìn thấy): cùng địa chỉ, cùng thông báo, không lộ email hay mật khẩu', async ({ browser, request }) => {
    const known = uniqueEmail();
    const unknown = uniqueEmail();
    const attempts = 3; // dưới giới hạn 5 lần mỗi cặp (email, địa chỉ mạng) trong 5 phút

    await registerByApi(request, known);

    // Một ngữ cảnh trình duyệt, xóa cookie giữa hai trường hợp: giữ bộ đệm tệp của giao diện để bớt yêu cầu qua cổng (mức chung 300 mỗi phút)
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    const wrongPassword = await failLogin(page, known, `${TEST_PASSWORD}x`, attempts);
    await ctx.clearCookies();
    const wrongEmail = await failLogin(page, unknown, TEST_PASSWORD, attempts);
    await ctx.close();

    const all = [...wrongPassword, ...wrongEmail];
    expect(new Set(all.map((r) => new URL(r.url).pathname + new URL(r.url).search)).size).toBe(1);
    expect(new URL(all[0].url).search).toBe('?error');
    expect(new Set(all.map((r) => r.message))).toEqual(new Set(['Email hoặc mật khẩu không đúng.']));
    for (const r of all) {
      expect(r.url).not.toContain(known);
      expect(r.url).not.toContain(unknown);
      expect(r.url).not.toContain(TEST_PASSWORD);
    }
  });

  test('KT-22 (S3, thời gian): sai mật khẩu và sai email tốn thời gian cùng bậc (một lần băm mỗi bên)', async ({ request, playwright }) => {
    test.setTimeout(180_000);
    const rounds = 4;
    const knownEmails: string[] = [];
    for (let k = 0; k < 5; k++) {
      const email = uniqueEmail();
      await registerByApi(request, email);
      knownEmails.push(email);
    }

    // Gửi thẳng form đăng nhập qua cổng (không qua trình duyệt) để bỏ nhiễu dựng trang; đo riêng yêu cầu POST.
    const attempt = async (email: string, password: string): Promise<{ ms: number; location: string }> => {
      const api = await playwright.request.newContext();
      const html = await (await api.get(`${AUTH}/login`)).text();
      const csrf = /name="_csrf"[^>]*value="([^"]+)"/.exec(html)?.[1] ?? /value="([^"]+)"[^>]*name="_csrf"/.exec(html)?.[1];
      expect(csrf, 'trang đăng nhập có mã CSRF').toBeTruthy();
      const started = performance.now();
      const res = await api.post(`${AUTH}/login`, { form: { username: email, password, _csrf: csrf! }, maxRedirects: 0 });
      const ms = performance.now() - started;
      expect(res.status()).toBe(302);
      const location = res.headers()['location'];
      await api.dispose();
      return { ms, location };
    };

    // Làm nóng (JIT, kết nối), bỏ khỏi kết quả
    await attempt(knownEmails[0], `${TEST_PASSWORD}x`);
    await attempt(uniqueEmail(), TEST_PASSWORD);

    // Xen kẽ hai nhánh để nhiễu của máy (xung nhịp, tiến trình nền) rơi đều vào cả hai
    const wrongPassword: number[] = [];
    const unknownEmail: number[] = [];
    for (let round = 0; round < rounds; round++) {
      for (const email of knownEmails) {
        const a = await attempt(email, `${TEST_PASSWORD}x`);
        const b = await attempt(uniqueEmail(), TEST_PASSWORD);
        expect(a.location).toBe(`${AUTH}/login?error`);
        expect(b.location).toBe(a.location);
        wrongPassword.push(a.ms);
        unknownEmail.push(b.ms);
      }
    }

    const mp = median(wrongPassword);
    const me = median(unknownEmail);
    console.log(`S3 thời gian trung vị (ms), ${wrongPassword.length} lần mỗi nhánh: sai mật khẩu ${Math.round(mp)}, sai email ${Math.round(me)}, lệch ${Math.round(mp - me)}`);
    // Bỏ băm giả (mất khoảng một lần băm, 45 đến 65 ms) sẽ làm lệch vượt ngưỡng; đo thực tế lệch trong ±10 ms
    expect(Math.abs(mp - me)).toBeLessThan(30);
  });
});
