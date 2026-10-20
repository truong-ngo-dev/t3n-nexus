import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { LogoutResult, PAGE_NAVIGATION, SESSION_URL, SessionService } from '@t3n/auth';

const LOGOUT_URL = '/bff/auth/logout';
const ACCEPTED = { status: 202, statusText: 'Accepted' };

describe('SessionService.logout (KT-08)', () => {
  let service: SessionService;
  let http: HttpTestingController;
  const assign = jest.fn();

  beforeEach(() => {
    assign.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: PAGE_NAVIGATION, useValue: { assign } },
      ],
    });
    service = TestBed.inject(SessionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function signIn(): void {
    service.probe().subscribe();
    http.expectOne(SESSION_URL).flush({ userId: 'u1', role: 'USER' });
  }

  function logout(respond: (req: ReturnType<HttpTestingController['expectOne']>) => void): LogoutResult[] {
    const results: LogoutResult[] = [];
    service.logout().subscribe((r) => results.push(r));
    respond(http.expectOne(LOGOUT_URL));
    return results;
  }

  it('gửi POST /bff/auth/logout', () => {
    logout((req) => {
      expect(req.request.method).toBe('POST');
      req.flush(null, { ...ACCEPTED, headers: { Location: 'http://auth.localhost:8000/connect/logout' } });
    });
  });

  it('202 kèm Location: điều hướng cấp cao tới đúng Location đó', () => {
    const results = logout((req) =>
      req.flush(null, { ...ACCEPTED, headers: { Location: 'http://auth.localhost:8000/connect/logout?id_token_hint=x' } }),
    );

    expect(assign).toHaveBeenCalledTimes(1);
    expect(assign).toHaveBeenCalledWith('http://auth.localhost:8000/connect/logout?id_token_hint=x');
    expect(results).toEqual([{ kind: 'redirected' }]);
  });

  it('phiên đã mất vẫn 202 kèm trang đích (đường tương đối): vẫn điều hướng (R8, KT-12)', () => {
    logout((req) => req.flush(null, { ...ACCEPTED, headers: { Location: '/' } }));

    expect(assign).toHaveBeenCalledWith('/');
  });

  it('202 thiếu Location: thất bại, không điều hướng', () => {
    const results = logout((req) => req.flush(null, ACCEPTED));

    expect(assign).not.toHaveBeenCalled();
    expect(results).toEqual([{ kind: 'failed' }]);
  });

  it.each(['javascript:alert(1)', 'data:text/html,x', '//evil.example/x'])('Location lạ %s: thất bại, không điều hướng', (location) => {
    const results = logout((req) => req.flush(null, { ...ACCEPTED, headers: { Location: location } }));

    expect(assign).not.toHaveBeenCalled();
    expect(results).toEqual([{ kind: 'failed' }]);
  });

  it.each([401, 403, 429, 500, 503])('%i: thất bại, không điều hướng, giữ nguyên trạng thái phiên (R9)', (status) => {
    signIn();
    const results = logout((req) => req.flush({}, { status, statusText: 'Error' }));

    expect(results).toEqual([{ kind: 'failed' }]);
    expect(assign).not.toHaveBeenCalled();
    expect(service.state()).toEqual({ kind: 'authenticated', session: { userId: 'u1', role: 'USER' } });
  });

  it('lỗi mạng: thất bại, giữ nguyên trạng thái, luồng kết thúc bình thường', () => {
    signIn();
    const results: LogoutResult[] = [];
    let failed = false;
    let completed = false;
    service.logout().subscribe({ next: (r) => results.push(r), error: () => (failed = true), complete: () => (completed = true) });
    http.expectOne(LOGOUT_URL).error(new ProgressEvent('error'));

    expect(results).toEqual([{ kind: 'failed' }]);
    expect(failed).toBe(false);
    expect(completed).toBe(true);
    expect(assign).not.toHaveBeenCalled();
    expect(service.state().kind).toBe('authenticated');
  });

  it('có thể đăng xuất lại sau một lần thất bại', () => {
    logout((req) => req.flush({}, { status: 503, statusText: 'Unavailable' }));
    logout((req) => req.flush(null, { ...ACCEPTED, headers: { Location: '/' } }));

    expect(assign).toHaveBeenCalledTimes(1);
  });
});
