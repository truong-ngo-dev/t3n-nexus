import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {ApplicationInitStatus} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {TestBed} from '@angular/core/testing';
import {LOGIN_URL, PAGE_NAVIGATION, SESSION_URL, SessionService} from '@t3n/auth';
import {appConfig} from './app.config';
import {SELLER_PROFILE_URL} from './core/service/seller-profile/seller-profile.api';
import {SellerStatusService} from './core/service/seller-profile/seller-status.service';

/*
 * Cách đọc một spec (Jest):
 *   describe(...)  nhóm các ca test.
 *   it(...)        một ca: một khẳng định về hành vi, tên viết như một câu.
 *   expect(a).toBe(b)  khẳng định; sai thì ca đỏ.
 *   beforeEach     chạy trước MỖI ca, để ca nào cũng bắt đầu từ trạng thái `sạch.
 *
 * TestBed: "ứng dụng thu nhỏ" của Angular dành cho test. Ta cho nó đúng các providers
 * của ứng dụng thật (appConfig.providers), rồi lấy service ra bằng TestBed.inject.
 *
 * HttpTestingController: thay phần gửi HTTP thật. Yêu cầu không ra mạng mà dừng lại ở đây,
 * test tự quyết lúc nào và trả lời gì (flush), nên mô phỏng được máy chủ chậm, máy chủ lỗi.
 */
describe('Cấu hình ứng dụng seller-web', () => {
  const assign = jest.fn(); // thay cho "điều hướng cấp cao" thật (jsdom không điều hướng được)
  let http: HttpTestingController;

  beforeEach(() => {
    assign.mockReset();
    TestBed.configureTestingModule({
      providers: [
        ...appConfig.providers, // cấu hình THẬT cần kiểm
        provideHttpClientTesting(), // đổi bộ gửi HTTP thành bộ điều khiển
        { provide: PAGE_NAVIGATION, useValue: { assign } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  // sau mỗi ca: không được còn yêu cầu HTTP nào chưa trả lời (bắt lỗi quên xử lý)
  afterEach(() => http.verify());

  // ---- CA 1 (ví dụ mẫu) ----
  it('khởi động chờ thăm dò phiên xong, kể cả khi phiên lỗi', async () => {
    // Khi TestBed dựng, các bộ khởi động (provideAppInitializer) bắt đầu chạy.
    // ApplicationInitStatus cho ta biết chúng đã xong chưa; donePromise là Promise "đã xong".
    let done = false;
    const init = TestBed.inject(ApplicationInitStatus);
    init.donePromise.then(() => (done = true));

    // Bước 1: bộ khởi động đã gửi đúng MỘT yêu cầu thăm dò phiên (nếu không có, expectOne ném lỗi).
    const req = http.expectOne(SESSION_URL);

    // Bước 2: máy chủ CHƯA trả lời. Nhường một nhịp cho các việc đang chờ chạy,
    // rồi khẳng định khởi động chưa xong. Đây là chỗ chứng minh "chờ thật".
    await Promise.resolve();
    expect(done).toBe(false);
    expect(TestBed.inject(SessionService).state().kind).toBe('loading');

    // Bước 3: máy chủ trả 503. probe() phải biến lỗi thành trạng thái 'unknown', không ném lỗi.
    req.flush(null, { status: 503, statusText: 'Service Unavailable' });
    await init.donePromise;

    // Bước 4: khởi động vẫn xong, và trạng thái là 'unknown' (không kẹt, không coi là khách).
    expect(done).toBe(true);
    expect(TestBed.inject(SessionService).state().kind).toBe('unknown');
  });

  // ---- CA 2, 3: bạn viết (cùng khuôn; xem gợi ý trong thông báo của mình) ----
  it('có bộ chặn CSRF: 403 CSRF_TOKEN_INVALID thì thăm dò phiên rồi gửi lại một lần', async () => {
    const init = TestBed.inject(ApplicationInitStatus);
    http.expectOne(SESSION_URL).flush({ userId: 'u', role: 'USER' });
    http.expectOne(SELLER_PROFILE_URL).flush(null, { status: 500, statusText: 'Server Error' }); // init chờ hồ sơ; lỗi bị nuốt
    await init.donePromise;
    let status = 0;
    TestBed.inject(HttpClient).post('/api/x', {}, { observe: 'response' }).subscribe((r) => (status = r.status));
    http.expectOne('/api/x').flush({ code: 'CSRF_TOKEN_INVALID' }, { status: 403, statusText: 'Forbidden' });
    http.expectOne(SESSION_URL).flush({ userId: 'u', role: 'USER' });
    http.expectOne('/api/x').flush({});
    expect(status).toBe(200);
  });

  it('có bộ chặn 401: API trả 401 thì đi đăng nhập', async () => {
    const init = TestBed.inject(ApplicationInitStatus);
    http.expectOne(SESSION_URL).flush({ userId: 'u', role: 'USER' });
    http.expectOne(SELLER_PROFILE_URL).flush(null, { status: 500, statusText: 'Server Error' });
    await init.donePromise;
    TestBed.inject(HttpClient).get('/api/x').subscribe({ error: () => undefined });
    http.expectOne('/api/x').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(assign).toHaveBeenCalledWith(LOGIN_URL);
  });

  it('phiên USER: gọi hồ sơ và khởi động chờ hồ sơ xong', async () => {
    let done = false;
    const init = TestBed.inject(ApplicationInitStatus);
    init.donePromise.then(() => (done = true));

    http.expectOne(SESSION_URL).flush({ userId: 'u', role: 'USER' });
    const profile = http.expectOne(SELLER_PROFILE_URL);       // có gọi hồ sơ

    await Promise.resolve();
    expect(done).toBe(false);                                  // chưa trả lời thì chưa xong

    profile.flush({ code: 'OK', data: { status: 'APPROVED' } });
    await init.donePromise;
    expect(TestBed.inject(SellerStatusService).state()).toEqual({ kind: 'approved' });
  });

  it('phiên ADMIN: không gọi hồ sơ, khởi động xong ngay', async () => {
    const init = TestBed.inject(ApplicationInitStatus);
    http.expectOne(SESSION_URL).flush({ userId: 'a', role: 'ADMIN' });
    await init.donePromise;

    http.expectNone(SELLER_PROFILE_URL);
    expect(TestBed.inject(SellerStatusService).state()).toEqual({ kind: 'loading' });
  });

  it.each([
    ['khách (401)', 401, 'Unauthorized'],
    ['phiên lỗi (503)', 503, 'Service Unavailable'],
  ])('%s: không gọi hồ sơ, khởi động vẫn xong', async (_name, status, statusText) => {
    const init = TestBed.inject(ApplicationInitStatus);
    http.expectOne(SESSION_URL).flush(null, { status, statusText });
    await init.donePromise;

    http.expectNone(SELLER_PROFILE_URL);
  });

  it('phiên USER nhưng hồ sơ lỗi 500: khởi động vẫn xong, trạng thái là unknown', async () => {
    const init = TestBed.inject(ApplicationInitStatus);
    http.expectOne(SESSION_URL).flush({ userId: 'u', role: 'USER' });
    http.expectOne(SELLER_PROFILE_URL).flush(null, { status: 500, statusText: 'Server Error' });
    await init.donePromise;

    expect(TestBed.inject(SellerStatusService).state()).toEqual({ kind: 'unknown' });
  });

  it('phiên USER chưa có hồ sơ (404 SELLER_PROFILE_NOT_FOUND): trạng thái là none', async () => {
    const init = TestBed.inject(ApplicationInitStatus);
    http.expectOne(SESSION_URL).flush({ userId: 'u', role: 'USER' });
    http.expectOne(SELLER_PROFILE_URL).flush({ code: 'SELLER_PROFILE_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    await init.donePromise;

    expect(TestBed.inject(SellerStatusService).state()).toEqual({ kind: 'none' });
  });
});
