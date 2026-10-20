import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PAGE_NAVIGATION, SESSION_URL, SessionService } from '@t3n/auth';

describe('SessionService (KT-01, KT-02)', () => {
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

  function probeAndRespond(respond: (req: ReturnType<HttpTestingController['expectOne']>) => void): void {
    service.probe().subscribe();
    respond(http.expectOne(SESSION_URL));
  }

  it('bắt đầu ở trạng thái đang kiểm, chưa gọi mạng', () => {
    expect(service.state()).toEqual({ kind: 'loading' });
  });

  it('thăm dò bằng GET tới /bff/auth/session', () => {
    probeAndRespond((req) => {
      expect(req.request.method).toBe('GET');
      expect(req.request.url).toBe('/bff/auth/session');
      req.flush({ userId: 'u1', role: 'USER' });
    });
  });

  // KT-01
  it('200 hợp lệ thành người dùng với vai USER', () => {
    probeAndRespond((req) => req.flush({ userId: 'u1', role: 'USER' }));
    expect(service.state()).toEqual({ kind: 'authenticated', session: { userId: 'u1', role: 'USER' } });
  });

  it('200 hợp lệ thành người dùng với vai ADMIN', () => {
    probeAndRespond((req) => req.flush({ userId: 'u9', role: 'ADMIN' }));
    expect(service.state()).toEqual({ kind: 'authenticated', session: { userId: 'u9', role: 'ADMIN' } });
  });

  it('401 thành khách', () => {
    probeAndRespond((req) => req.flush({}, { status: 401, statusText: 'Unauthorized' }));
    expect(service.state()).toEqual({ kind: 'anonymous' });
  });

  it('200 với vai lạ bị từ chối: không xác định, không phải người dùng', () => {
    probeAndRespond((req) => req.flush({ userId: 'u1', role: 'SUPERUSER' }));
    expect(service.state()).toEqual({ kind: 'unknown' });
  });

  it('200 thiếu userId: không xác định', () => {
    probeAndRespond((req) => req.flush({ role: 'USER' }));
    expect(service.state()).toEqual({ kind: 'unknown' });
  });

  it('200 sai khuôn (không phải JSON đối tượng): không xác định', () => {
    probeAndRespond((req) => req.flush('<html></html>'));
    expect(service.state()).toEqual({ kind: 'unknown' });
  });

  // KT-02
  it('401 là khách và KHÔNG chuyển hướng', () => {
    probeAndRespond((req) => req.flush({}, { status: 401, statusText: 'Unauthorized' }));
    expect(assign).not.toHaveBeenCalled();
  });

  it.each([500, 502, 503])('%i là không xác định, không phải khách', (status) => {
    probeAndRespond((req) => req.flush({}, { status, statusText: 'Error' }));
    expect(service.state()).toEqual({ kind: 'unknown' });
    expect(assign).not.toHaveBeenCalled();
  });

  it('lỗi mạng là không xác định, không phải khách', () => {
    probeAndRespond((req) => req.error(new ProgressEvent('error')));
    expect(service.state()).toEqual({ kind: 'unknown' });
  });

  it('thử lại sau lỗi: dịch vụ hồi phục thì vào được', () => {
    probeAndRespond((req) => req.flush({}, { status: 503, statusText: 'Unavailable' }));
    expect(service.state()).toEqual({ kind: 'unknown' });

    probeAndRespond((req) => req.flush({ userId: 'u1', role: 'USER' }));
    expect(service.state()).toEqual({ kind: 'authenticated', session: { userId: 'u1', role: 'USER' } });
  });

  it('thăm dò lại sau khi mất phiên chuyển người dùng thành khách', () => {
    probeAndRespond((req) => req.flush({ userId: 'u1', role: 'USER' }));
    probeAndRespond((req) => req.flush({}, { status: 401, statusText: 'Unauthorized' }));
    expect(service.state()).toEqual({ kind: 'anonymous' });
  });

  it('thăm dò hoàn tất (kể cả lỗi) thì luồng kết thúc bình thường, không ném lỗi', () => {
    let completed = false;
    let failed = false;
    service.probe().subscribe({ complete: () => (completed = true), error: () => (failed = true) });
    http.expectOne(SESSION_URL).flush({}, { status: 500, statusText: 'Error' });
    expect(completed).toBe(true);
    expect(failed).toBe(false);
  });
});
