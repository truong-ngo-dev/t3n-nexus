import { expect, test } from '@playwright/test';
import { rawLogTail, waitForLogLine } from './support/logs';

const AUTH = 'http://auth.localhost:8000';

test.describe('truy vết lỗi qua cổng (feature 06 và 07)', () => {
  test('KT-23: yêu cầu lỗi qua cổng trả traceId; log của cổng và của Xác thực có đúng trace.id đó và mã lỗi; mật khẩu không vào log', async ({ request }) => {
    const secret = `LeakCheck${Date.now().toString(36)}Pwd`;
    // JSON hỏng (thiếu dấu đóng) có kèm mật khẩu: Xác thực trả 400 MALFORMED_REQUEST
    const res = await request.post(`${AUTH}/register`, {
      headers: { 'content-type': 'application/json' },
      data: Buffer.from(`{"email":"e2e-trace@example.com","password":"${secret}`), // Buffer: gửi nguyên byte, JSON hỏng thật (chuỗi sẽ bị Playwright mã hóa thành chuỗi JSON hợp lệ)
    });
    expect(res.status()).toBe(400);
    const body = await res.json();
    expect(body.code).toBe('MALFORMED_REQUEST');
    const traceId: string = body.traceId;
    expect(traceId).toMatch(/^[0-9a-f]{32}$/);

    // Dịch vụ bắt lỗi: dòng hoàn tất của yêu cầu mang đúng trace.id và mã lỗi
    const authLine = await waitForLogLine('oauth2-service', (l) => l['trace.id'] === traceId && l.message === 'POST /register 400');
    expect(authLine, 'dòng log POST /register 400 của oauth2-service với trace.id của phản hồi').toBeDefined();
    expect((authLine!['error'] as { code?: string } | undefined)?.code).toBe('MALFORMED_REQUEST');

    // Cổng cũng ghi đúng trace.id đó: nối được một yêu cầu qua hai dịch vụ
    const gatewayLine = await waitForLogLine('api-gateway', (l) => l['trace.id'] === traceId);
    expect(gatewayLine, 'dòng log của api-gateway với cùng trace.id').toBeDefined();
    expect(gatewayLine!.message).toContain('POST /register');

    // Mật khẩu trong nội dung hỏng không vào log của dịch vụ nào, ở bất kỳ dòng nào
    expect(rawLogTail('oauth2-service')).not.toContain(secret);
    expect(rawLogTail('api-gateway')).not.toContain(secret);
  });

  // Đã sửa ở feature 07 sau khi feature 09 tìm ra: nội dung là một chuỗi JSON hợp lệ (JSON mã hóa hai lần, lỗi hay gặp ở phía gọi) từng lọt mật khẩu vào log
  // ở dòng hoàn tất của yêu cầu (BodySanitizer che theo tên trường, chuỗi trơn không có tên) và ở thông báo của GlobalExceptionHandler (Jackson đưa giá trị vào thông báo).
  test('KT-24: nội dung là chuỗi JSON hợp lệ có mật khẩu bên trong: mật khẩu không vào log', async ({ request }) => {
    const secret = `LeakCheck${Date.now().toString(36)}Str`;
    const res = await request.post(`${AUTH}/register`, {
      headers: { 'content-type': 'application/json' },
      data: Buffer.from(JSON.stringify(JSON.stringify({ email: 'e2e-leak@example.com', password: secret }))),
    });
    expect(res.status()).toBe(400);
    const traceId: string = (await res.json()).traceId;
    await waitForLogLine('oauth2-service', (l) => l['trace.id'] === traceId && l.message === 'POST /register 400');
    expect(rawLogTail('oauth2-service')).not.toContain(secret);
  });
});
