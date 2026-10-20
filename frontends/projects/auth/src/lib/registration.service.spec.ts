import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AUTH_BASE_URL, RegisterResult, RegistrationService, registerErrorMessage } from '@t3n/auth';

const BASE = 'http://auth.test';

describe('RegistrationService (KT-07)', () => {
  let service: RegistrationService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AUTH_BASE_URL, useValue: BASE }],
    });
    service = TestBed.inject(RegistrationService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function register(respond: (req: ReturnType<HttpTestingController['expectOne']>) => void): RegisterResult[] {
    const results: RegisterResult[] = [];
    service.register('a@b.vn', 'Passw0rd').subscribe((r) => results.push(r));
    respond(http.expectOne(`${BASE}/register`));
    return results;
  }

  const failWith = (status: number, error: unknown, headers: Record<string, string> = {}) => (req: ReturnType<HttpTestingController['expectOne']>) =>
    req.flush(error, { status, statusText: String(status), headers });

  it('POST tới host Xác thực, chỉ gửi {email, password}, không mang cookie', () => {
    register((req) => {
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ email: 'a@b.vn', password: 'Passw0rd' });
      expect(Object.keys(req.request.body as object).sort()).toEqual(['email', 'password']);
      expect(req.request.withCredentials).toBe(false);
      req.flush({ success: true, data: { userAccountId: 'id' } }, { status: 201, statusText: 'Created' });
    });
  });

  it('không gắn X-XSRF-TOKEN cho lời gọi khác nguồn gốc (miễn CSRF)', () => {
    document.cookie = 'XSRF-TOKEN=abc; path=/';
    register((req) => {
      expect(req.request.headers.has('X-XSRF-TOKEN')).toBe(false);
      req.flush({}, { status: 201, statusText: 'Created' });
    });
  });

  it('201: đã tạo', () => {
    const results = register((req) => req.flush({}, { status: 201, statusText: 'Created' }));

    expect(results).toEqual([{ kind: 'created' }]);
  });

  it.each([
    ['EMAIL_INVALID', 'email-unusable'],
    ['EMAIL_RESERVED', 'email-unusable'],
    ['PASSWORD_INVALID', 'password-invalid'],
    ['VALIDATION_FAILED', 'invalid'],
  ])('400 %s thành %s', (code, kind) => {
    const results = register(failWith(400, { success: false, code, message: 'x' }));

    expect(results).toEqual([{ kind: 'failed', failure: { kind } }]);
  });

  it('409 EMAIL_TAKEN: email đã đăng ký', () => {
    const results = register(failWith(409, { success: false, code: 'EMAIL_TAKEN', message: 'x' }));

    expect(results).toEqual([{ kind: 'failed', failure: { kind: 'email-taken' } }]);
  });

  it('429: giữ số giây từ Retry-After', () => {
    const results = register(failWith(429, { code: 'TOO_MANY_REQUESTS' }, { 'Retry-After': '45' }));

    expect(results).toEqual([{ kind: 'failed', failure: { kind: 'rate-limited', retryAfterSeconds: 45 } }]);
  });

  it('429 thiếu hoặc sai Retry-After: không đoán số giây', () => {
    const missing = register(failWith(429, {}));
    expect(missing[0]).toEqual({ kind: 'failed', failure: { kind: 'rate-limited' } });
  });

  it.each([500, 503, 0])('lỗi %i: thử lại sau', (status) => {
    const results = register(failWith(status, null));

    expect(results).toEqual([{ kind: 'failed', failure: { kind: 'other' } }]);
  });

  it('400 mã lạ hoặc thiếu mã: lỗi chung, KHÔNG đoán lý do từ chữ của thông điệp (R6)', () => {
    const results = register(failWith(400, { success: false, message: 'Email đã tồn tại' }));

    expect(results).toEqual([{ kind: 'failed', failure: { kind: 'other' } }]);
  });

  it('luồng luôn hoàn tất, không ném lỗi ra ngoài', () => {
    let errored = false;
    let completed = false;
    service.register('a@b.vn', 'Passw0rd').subscribe({ error: () => (errored = true), complete: () => (completed = true) });
    failWith(503, null)(http.expectOne(`${BASE}/register`));

    expect(errored).toBe(false);
    expect(completed).toBe(true);
  });
});

describe('registerErrorMessage (R6)', () => {
  it('mỗi loại lỗi một thông báo riêng, đúng chữ của flow.md', () => {
    expect(registerErrorMessage({ kind: 'email-taken' })).toBe('Email đã được đăng ký');
    expect(registerErrorMessage({ kind: 'email-unusable' })).toBe('Email không dùng được để đăng ký');
    expect(registerErrorMessage({ kind: 'password-invalid' })).toMatch(/8 đến 64/);
    expect(registerErrorMessage({ kind: 'other' })).toMatch(/thử lại sau/i);
  });

  it('429 nêu số giây chờ khi có, không nêu số khi không có', () => {
    expect(registerErrorMessage({ kind: 'rate-limited', retryAfterSeconds: 45 })).toContain('45');
    expect(registerErrorMessage({ kind: 'rate-limited' })).not.toMatch(/\d/);
  });
});
