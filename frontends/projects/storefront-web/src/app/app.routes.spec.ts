import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AUTH_BASE_URL, LOGIN_URL, PAGE_NAVIGATION } from '@t3n/auth';
import { routes } from './app.routes';
import { asUser, probeWith } from './testing/session-testing';

describe('Định tuyến (tech.md T3)', () => {
  const assign = jest.fn();

  beforeEach(() => {
    assign.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter(routes),
        { provide: PAGE_NAVIGATION, useValue: { assign } },
        { provide: AUTH_BASE_URL, useValue: 'http://auth.test' },
      ],
    });
  });

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  const body = (h: RouterTestingHarness) => (h.routeNativeElement as HTMLElement | null)?.textContent ?? '';

  it('/ là trang chủ, khách vào được', async () => {
    probeWith({ status: 401 });
    const h = await RouterTestingHarness.create('/');

    expect(body(h)).toContain('T3N Storefront');
    expect(assign).not.toHaveBeenCalled();
  });

  it('/account khi đã đăng nhập: hiện mã tài khoản và vai', async () => {
    probeWith(asUser('USER'));
    const h = await RouterTestingHarness.create('/account');

    expect(body(h)).toContain('u-123');
    expect(body(h)).toContain('Người dùng');
  });

  it('/account với Quản trị: hiện vai "Quản trị"', async () => {
    probeWith(asUser('ADMIN'));
    const h = await RouterTestingHarness.create('/account');

    expect(body(h)).toContain('Quản trị');
  });

  it('/account khi là khách: đi đăng nhập một lần, không hiện trang', async () => {
    probeWith({ status: 401 });
    const h = await RouterTestingHarness.create('/account');

    expect(assign).toHaveBeenCalledTimes(1);
    expect(assign).toHaveBeenCalledWith(LOGIN_URL);
    expect(body(h)).not.toContain('u-123');
  });

  // KT-19, R16
  it('địa chỉ lạ: hiện trang không tìm thấy, giữ nguyên địa chỉ, không chuyển hướng ngầm về trang chủ', async () => {
    probeWith({ status: 401 });
    const h = await RouterTestingHarness.create('/khong-co-trang-nay');

    expect(body(h)).toContain('Không tìm thấy trang');
    expect(TestBed.inject(Location).path()).toBe('/khong-co-trang-nay');
  });

  it('trang không tìm thấy có đường về trang chủ', async () => {
    probeWith({ status: 401 });
    const h = await RouterTestingHarness.create('/xyz');

    expect((h.routeNativeElement as HTMLElement).querySelector('a[href="/"]')).not.toBeNull();
  });

  it('/register: khách vào được màn Đăng ký', async () => {
    probeWith({ status: 401 });
    const h = await RouterTestingHarness.create('/register');

    expect(body(h)).toContain('Đăng ký');
    expect(assign).not.toHaveBeenCalled();
  });

  it('/register khi đã đăng nhập: về /account (guestOnlyGuard, sửa vòng lặp R1)', async () => {
    probeWith(asUser('USER'));
    await RouterTestingHarness.create('/register');

    expect(TestBed.inject(Router).url).toBe('/account');
  });

  it('/login: khách vào được màn Đăng nhập', async () => {
    probeWith({ status: 401 });
    const h = await RouterTestingHarness.create('/login');

    expect(body(h)).toContain('Đăng nhập');
    expect(assign).not.toHaveBeenCalled();
  });

  it('/login khi đã đăng nhập: về /account (sửa vòng lặp R1)', async () => {
    probeWith(asUser('USER'));
    await RouterTestingHarness.create('/login');

    expect(TestBed.inject(Router).url).toBe('/account');
  });

  it('trang chủ cho khách: có nút Đăng ký và Đăng nhập', async () => {
    probeWith({ status: 401 });
    const h = await RouterTestingHarness.create('/');
    const el = h.routeNativeElement as HTMLElement;

    expect(el.querySelector('a[href="/register"]')).not.toBeNull();
    expect(el.querySelector('a[href="/login"]')).not.toBeNull();
  });

  it('trang chủ cho người đã đăng nhập: không còn nút Đăng ký, Đăng nhập', async () => {
    probeWith(asUser('USER'));
    const h = await RouterTestingHarness.create('/');
    const el = h.routeNativeElement as HTMLElement;

    expect(el.querySelector('a[href="/register"]')).toBeNull();
    expect(el.querySelector('a[href="/login"]')).toBeNull();
  });
});
