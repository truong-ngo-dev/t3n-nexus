import { HttpClient, HttpResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpBackend } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { PAGE_NAVIGATION, SESSION_URL, SessionService } from '@t3n/auth';
import { csrfRecoveryInterceptor } from '@t3n/http';
import { FakeBackend, Reply, accepted, fail, ok } from './testing/fake-backend';

const CSRF = { success: false, code: 'CSRF_TOKEN_INVALID' };
const FORBIDDEN = { success: false, code: 'FORBIDDEN' };
const SESSION = { userId: 'u1', role: 'USER' };

describe('csrfRecoveryInterceptor (KT-04)', () => {
  let backend: FakeBackend;
  let http: HttpClient;

  function setup(script: Reply[]): void {
    backend = new FakeBackend(script);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([csrfRecoveryInterceptor])),
        { provide: HttpBackend, useValue: backend },
        { provide: PAGE_NAVIGATION, useValue: { assign: jest.fn() } },
      ],
    });
    http = TestBed.inject(HttpClient);
  }

  function post(): { responses: unknown[]; errors: unknown[] } {
    const out = { responses: [] as unknown[], errors: [] as unknown[] };
    http.post('/api/orders', {}, { observe: 'response' }).subscribe({
      next: (r) => out.responses.push(r),
      error: (e) => out.errors.push(e),
    });
    return out;
  }

  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=old; path=/';
  });

  it('403 CSRF_TOKEN_INVALID: thăm dò phiên rồi gửi lại đúng một lần, người gọi nhận một phản hồi', () => {
    setup([fail(403, CSRF), ok(SESSION), ok({ id: 1 })]);
    const out = post();

    expect(backend.calls).toEqual(['POST /api/orders', `GET ${SESSION_URL}`, 'POST /api/orders']);
    expect(out.errors).toEqual([]);
    expect(out.responses).toHaveLength(1);
    expect((out.responses[0] as HttpResponse<unknown>).body).toEqual({ id: 1 });
  });

  it('gửi lại mang mã XSRF mới được cấp bởi lần thăm dò, không phải mã cũ', () => {
    setup([
      fail(403, CSRF),
      ok(SESSION, () => (document.cookie = 'XSRF-TOKEN=fresh; path=/')),
      ok(),
    ]);
    post();

    expect(backend.requests[0].headers.get('X-XSRF-TOKEN')).toBe('old');
    expect(backend.requests[2].headers.get('X-XSRF-TOKEN')).toBe('fresh');
  });

  it('lần hai vẫn 403 CSRF: trả lỗi, không thử lần ba (không vòng lặp)', () => {
    setup([fail(403, CSRF), ok(SESSION), fail(403, CSRF)]);
    const out = post();

    expect(backend.calls).toEqual(['POST /api/orders', `GET ${SESSION_URL}`, 'POST /api/orders']);
    expect(out.responses).toEqual([]);
    expect(out.errors).toHaveLength(1);
    expect((out.errors[0] as { status: number }).status).toBe(403);
  });

  it('403 FORBIDDEN: không thử lại, trả lỗi ngay', () => {
    setup([fail(403, FORBIDDEN)]);
    const out = post();

    expect(backend.calls).toEqual(['POST /api/orders']);
    expect(out.errors).toHaveLength(1);
  });

  it.each([401, 429, 500, 503, 0])('lỗi %i: không thử lại', (status) => {
    setup([fail(status)]);
    const out = post();

    expect(backend.calls).toEqual(['POST /api/orders']);
    expect(out.errors).toHaveLength(1);
  });

  it('thành công: đi thẳng một lần, không thăm dò', () => {
    setup([ok({ id: 1 })]);
    const out = post();

    expect(backend.calls).toEqual(['POST /api/orders']);
    expect(out.responses).toHaveLength(1);
  });

  it('GET bị 403 CSRF (không thể xảy ra với yêu cầu đọc): không thử lại', () => {
    setup([fail(403, CSRF)]);
    http.get('/api/orders').subscribe({ error: () => undefined });

    expect(backend.calls).toEqual(['GET /api/orders']);
  });

  it('thăm dò trong lúc phục hồi cập nhật trạng thái phiên', () => {
    setup([fail(403, CSRF), ok(SESSION), ok()]);
    post();

    expect(TestBed.inject(SessionService).state()).toEqual({ kind: 'authenticated', session: SESSION });
  });

  // KT-08: đăng xuất đi qua bộ chặn như mọi yêu cầu ghi
  it('đăng xuất bị 403 CSRF: phục hồi rồi gửi lại, rồi điều hướng tới Location', () => {
    const assign = jest.fn();
    backend = new FakeBackend([fail(403, CSRF), ok(SESSION), accepted('http://auth.localhost:8000/connect/logout')]);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([csrfRecoveryInterceptor])),
        { provide: HttpBackend, useValue: backend },
        { provide: PAGE_NAVIGATION, useValue: { assign } },
      ],
    });
    const results: unknown[] = [];
    TestBed.inject(SessionService).logout().subscribe((r) => results.push(r));

    expect(backend.calls).toEqual(['POST /bff/auth/logout', `GET ${SESSION_URL}`, 'POST /bff/auth/logout']);
    expect(results).toEqual([{ kind: 'redirected' }]);
    expect(assign).toHaveBeenCalledWith('http://auth.localhost:8000/connect/logout');
  });

  it('bắt đầu mỗi yêu cầu độc lập: phục hồi cho yêu cầu này không làm mất quyền phục hồi của yêu cầu sau', () => {
    setup([fail(403, CSRF), ok(SESSION), ok(), fail(403, CSRF), ok(SESSION), ok()]);
    post();
    post();

    expect(backend.calls).toEqual([
      'POST /api/orders', `GET ${SESSION_URL}`, 'POST /api/orders',
      'POST /api/orders', `GET ${SESSION_URL}`, 'POST /api/orders',
    ]);
  });
});
