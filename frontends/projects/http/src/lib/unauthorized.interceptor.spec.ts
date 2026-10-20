import { HttpBackend, HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { LOGIN_URL, PAGE_NAVIGATION, SESSION_URL } from '@t3n/auth';
import { JUST_SIGNED_IN_FLAG, unauthorizedInterceptor } from '@t3n/http';
import { FakeBackend, Reply, fail, ok } from './testing/fake-backend';

describe('unauthorizedInterceptor (KT-05)', () => {
  const assign = jest.fn();
  let http: HttpClient;

  function setup(script: Reply[]): FakeBackend {
    const backend = new FakeBackend(script);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([unauthorizedInterceptor])),
        { provide: HttpBackend, useValue: backend },
        { provide: PAGE_NAVIGATION, useValue: { assign } },
      ],
    });
    http = TestBed.inject(HttpClient);
    return backend;
  }

  function call(url = '/api/orders'): unknown[] {
    const errors: unknown[] = [];
    http.get(url).subscribe({ error: (e) => errors.push(e) });
    return errors;
  }

  beforeEach(() => {
    assign.mockReset();
    sessionStorage.clear();
  });

  it('401 khi chưa có cờ: đặt cờ, đi đăng nhập đúng một lần, vẫn trả lỗi 401 cho người gọi', () => {
    setup([fail(401)]);
    const errors = call();

    expect(sessionStorage.getItem(JUST_SIGNED_IN_FLAG)).not.toBeNull();
    expect(assign).toHaveBeenCalledTimes(1);
    expect(assign).toHaveBeenCalledWith(LOGIN_URL);
    expect(errors).toHaveLength(1);
    expect((errors[0] as { status: number }).status).toBe(401);
  });

  it('401 khi đã có cờ (vừa đăng nhập mà vẫn 401): dừng, không đi đăng nhập, xóa cờ, trả lỗi', () => {
    sessionStorage.setItem(JUST_SIGNED_IN_FLAG, '1');
    setup([fail(401)]);
    const errors = call();

    expect(assign).not.toHaveBeenCalled();
    expect(sessionStorage.getItem(JUST_SIGNED_IN_FLAG)).toBeNull();
    expect(errors).toHaveLength(1);
  });

  it('vòng lặp sau đăng nhập: 401, đi đăng nhập, tải lại trang mà vẫn 401 thì dừng ở lần hai', () => {
    setup([fail(401)]);
    call(); // lần một: đi đăng nhập
    expect(assign).toHaveBeenCalledTimes(1);

    TestBed.resetTestingModule(); // trang tải lại sau đăng nhập; sessionStorage còn nguyên
    setup([fail(401)]);
    call();

    expect(assign).toHaveBeenCalledTimes(1);
    expect(sessionStorage.getItem(JUST_SIGNED_IN_FLAG)).toBeNull();
  });

  it('nhiều 401 song song chưa có cờ: chỉ đi đăng nhập một lần', () => {
    setup([fail(401)]);
    call('/api/a');
    call('/api/b');
    call('/api/c');

    expect(assign).toHaveBeenCalledTimes(1);
  });

  it('phản hồi thành công của API xóa cờ', () => {
    sessionStorage.setItem(JUST_SIGNED_IN_FLAG, '1');
    setup([ok({})]);
    call();

    expect(sessionStorage.getItem(JUST_SIGNED_IN_FLAG)).toBeNull();
    expect(assign).not.toHaveBeenCalled();
  });

  it('thăm dò phiên thành công KHÔNG xóa cờ (không thì 401 kế tiếp lại đi đăng nhập, vòng lặp R2)', () => {
    sessionStorage.setItem(JUST_SIGNED_IN_FLAG, '1');
    setup([ok({ userId: 'u1', role: 'USER' })]);
    call(SESSION_URL);

    expect(sessionStorage.getItem(JUST_SIGNED_IN_FLAG)).not.toBeNull();
  });

  it('401 của thăm dò phiên là trạng thái khách bình thường: không cờ, không đi đăng nhập', () => {
    setup([fail(401)]);
    const errors = call(SESSION_URL);

    expect(assign).not.toHaveBeenCalled();
    expect(sessionStorage.getItem(JUST_SIGNED_IN_FLAG)).toBeNull();
    expect(errors).toHaveLength(1);
  });

  it.each([0, 400, 403, 429, 500, 503])('lỗi %i không đụng tới cờ và không đi đăng nhập', (status) => {
    setup([fail(status)]);
    const errors = call();

    expect(assign).not.toHaveBeenCalled();
    expect(sessionStorage.getItem(JUST_SIGNED_IN_FLAG)).toBeNull();
    expect(errors).toHaveLength(1);
  });

  it('sessionStorage chỉ chứa cờ, không chứa gì khác (R11, KT-17)', () => {
    setup([fail(401)]);
    call();

    expect(sessionStorage.length).toBe(1);
    expect(sessionStorage.key(0)).toBe(JUST_SIGNED_IN_FLAG);
    expect(sessionStorage.getItem(JUST_SIGNED_IN_FLAG)).toBe('1');
  });
});
