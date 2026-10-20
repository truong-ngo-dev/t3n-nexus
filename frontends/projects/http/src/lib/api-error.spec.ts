import { HttpBackend, HttpClient, HttpErrorResponse, HttpHeaders, provideHttpClient, withInterceptors } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { PAGE_NAVIGATION, SESSION_URL, SessionService } from '@t3n/auth';
import { csrfRecoveryInterceptor, toApiError, unauthorizedInterceptor } from '@t3n/http';
import { FakeBackend, fail, ok } from './testing/fake-backend';

function response(status: number, error: unknown = null, headers: Record<string, string> = {}): HttpErrorResponse {
  return new HttpErrorResponse({ status, error, headers: new HttpHeaders(headers) });
}

describe('toApiError (KT-06)', () => {
  it('429 kèm Retry-After số giây: giữ số giây để hiện thông báo', () => {
    expect(toApiError(response(429, null, { 'Retry-After': '30' }))).toEqual({ kind: 'rate-limited', status: 429, retryAfterSeconds: 30 });
  });

  it.each([[{}], [{ 'Retry-After': 'soon' }], [{ 'Retry-After': '-5' }], [{ 'Retry-After': '' }]])(
    '429 thiếu hoặc sai Retry-After %j: không đoán số giây',
    (headers) => {
      const mapped = toApiError(response(429, null, headers));
      expect(mapped.kind).toBe('rate-limited');
      expect(mapped.retryAfterSeconds).toBeUndefined();
    },
  );

  it('503 là tạm thời, hiện thử lại', () => {
    expect(toApiError(response(503))).toEqual({ kind: 'unavailable', status: 503 });
  });

  it('mất mạng (status 0) là lỗi mạng', () => {
    expect(toApiError(response(0))).toEqual({ kind: 'network', status: 0 });
  });

  it('401 là chưa đăng nhập', () => {
    expect(toApiError(response(401, { code: 'UNAUTHENTICATED' })).kind).toBe('unauthenticated');
  });

  it('403 CSRF_TOKEN_INVALID tách khỏi 403 FORBIDDEN', () => {
    expect(toApiError(response(403, { code: 'CSRF_TOKEN_INVALID' })).kind).toBe('csrf');
    expect(toApiError(response(403, { code: 'FORBIDDEN' })).kind).toBe('forbidden');
    expect(toApiError(response(403)).kind).toBe('forbidden');
  });

  it('lỗi khác giữ mã trạng thái và mã lỗi của khung lỗi chung, không đoán từ chữ thông điệp', () => {
    expect(toApiError(response(409, { code: 'EMAIL_TAKEN', message: 'Email đã tồn tại' }))).toEqual({ kind: 'other', status: 409, code: 'EMAIL_TAKEN' });
  });

  it('nội dung lỗi không phải đối tượng hoặc mã không phải chuỗi: bỏ mã', () => {
    expect(toApiError(response(500, 'oops'))).toEqual({ kind: 'other', status: 500 });
    expect(toApiError(response(400, { code: 7 }))).toEqual({ kind: 'other', status: 400 });
  });

  it('giá trị không phải HttpErrorResponse: lỗi khác, không ném', () => {
    expect(toApiError(new Error('boom'))).toEqual({ kind: 'other' });
    expect(toApiError(undefined)).toEqual({ kind: 'other' });
  });
});

describe('lỗi 429, 503, mạng không đổi trạng thái đăng nhập (KT-06)', () => {
  const assign = jest.fn();

  beforeEach(() => {
    assign.mockReset();
    sessionStorage.clear();
  });

  it.each([429, 503, 0])('lỗi %i qua cả hai bộ chặn: giữ trạng thái phiên, không đi đăng nhập, không đặt cờ', (status) => {
    const backend = new FakeBackend([ok({ userId: 'u1', role: 'USER' }), fail(status)]);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([unauthorizedInterceptor, csrfRecoveryInterceptor])),
        { provide: HttpBackend, useValue: backend },
        { provide: PAGE_NAVIGATION, useValue: { assign } },
      ],
    });
    const session = TestBed.inject(SessionService);
    session.probe().subscribe();
    const before = session.state();
    expect(before.kind).toBe('authenticated');

    TestBed.inject(HttpClient).post('/api/orders', {}).subscribe({ error: () => undefined });

    expect(backend.calls).toEqual([`GET ${SESSION_URL}`, 'POST /api/orders']);
    expect(session.state()).toBe(before);
    expect(assign).not.toHaveBeenCalled();
    expect(sessionStorage.length).toBe(0);
  });
});
