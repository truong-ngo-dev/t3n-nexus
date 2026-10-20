import { HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationInitStatus } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { LOGIN_URL, PAGE_NAVIGATION, SESSION_URL, SessionService } from '@t3n/auth';
import { appConfig } from './app.config';

describe('Cấu hình ứng dụng', () => {
  const assign = jest.fn();
  let http: HttpTestingController;

  beforeEach(() => {
    assign.mockReset();
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [...appConfig.providers, provideHttpClientTesting(), { provide: PAGE_NAVIGATION, useValue: { assign } }],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lúc khởi động thăm dò phiên và chờ xong rồi mới coi là khởi động xong (kể cả khi lỗi)', async () => {
    let done = false;
    const init = TestBed.inject(ApplicationInitStatus);
    init.donePromise.then(() => (done = true));

    const req = http.expectOne(SESSION_URL);
    await Promise.resolve();
    expect(done).toBe(false);
    expect(TestBed.inject(SessionService).state().kind).toBe('loading');

    req.flush(null, { status: 503, statusText: 'x' });
    await init.donePromise;
    expect(done).toBe(true);
    expect(TestBed.inject(SessionService).state().kind).toBe('unknown');
  });

  it('có bộ chặn CSRF: 403 CSRF_TOKEN_INVALID thì thăm dò phiên rồi gửi lại một lần', async () => {
    const init = TestBed.inject(ApplicationInitStatus);
    http.expectOne(SESSION_URL).flush({ userId: 'u', role: 'USER' });
    await init.donePromise;

    let status = 0;
    TestBed.inject(HttpClient).post('/api/x', {}, { observe: 'response' }).subscribe((r) => (status = r.status));
    http.expectOne('/api/x').flush({ code: 'CSRF_TOKEN_INVALID' }, { status: 403, statusText: 'Forbidden' });
    http.expectOne(SESSION_URL).flush({ userId: 'u', role: 'USER' });
    http.expectOne('/api/x').flush({});

    expect(status).toBe(200);
  });

  it('có bộ chặn 401: API 401 thì đi đăng nhập một lần', async () => {
    const init = TestBed.inject(ApplicationInitStatus);
    http.expectOne(SESSION_URL).flush({ userId: 'u', role: 'USER' });
    await init.donePromise;

    TestBed.inject(HttpClient).get('/api/x').subscribe({ error: () => undefined });
    http.expectOne('/api/x').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(assign).toHaveBeenCalledWith(LOGIN_URL);
  });
});
