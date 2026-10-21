import { expect, test } from '@playwright/test';

const AUTH = 'http://auth.localhost:8000';
const LIMIT = 10; // luật mặc định của cổng: POST /register ở host auth, 10 yêu cầu mỗi giờ mỗi địa chỉ mạng (06-access-gateway)

/**
 * Chỉ chạy khi cổng chạy với luật mặc định (`scenarios/up.sh --rules default`, pha B của feature 09) và đặt `E2E_GATEWAY_RULES=default`.
 * Bộ thật thường ngày chạy cổng với luật đăng ký được nâng lên 100.000 (nếu không 10 lần đăng ký mỗi giờ là hết), nên test này bị bỏ qua ở đó.
 */
test.describe('giới hạn tần suất của đăng ký qua cổng (feature 01 và 06)', () => {
  test.skip(process.env.E2E_GATEWAY_RULES !== 'default', 'Cần cổng chạy luật mặc định (E2E_GATEWAY_RULES=default)');

  test('KT-25: vượt 10 yêu cầu POST /register mỗi giờ thì 429 kèm Retry-After và thân lỗi chuẩn', async ({ request }) => {
    // Nội dung hợp lệ về cú pháp nhưng sai (email lạ) để không tạo tài khoản: luật đếm mọi yêu cầu, bất kể kết quả
    const send = () => request.post(`${AUTH}/register`, { data: { email: 'khong-phai-email', password: 'x' } });

    for (let i = 1; i <= LIMIT; i++) {
      const res = await send();
      expect(res.status(), `yêu cầu thứ ${i} nằm trong mức`).toBe(400);
    }

    const over = await send();
    expect(over.status()).toBe(429);
    const retryAfter = Number(over.headers()['retry-after']);
    expect(retryAfter, 'Retry-After là số giây dương').toBeGreaterThan(0);
    expect(retryAfter).toBeLessThanOrEqual(3600);
    const body = await over.json();
    expect(body.success).toBe(false);
    expect(body.code).toBe('TOO_MANY_REQUESTS');
    expect(typeof body.message).toBe('string');

    // Luật chỉ gắn với đường dẫn đăng ký: đường dẫn khác ở cùng host vẫn đi qua
    const other = await request.get(`${AUTH}/.well-known/openid-configuration`);
    expect(other.status()).toBe(200);
  });
});
