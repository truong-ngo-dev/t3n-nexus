import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { PAGE_NAVIGATION, SESSION_URL } from '@t3n/auth';
import { AppComponent } from './app.component';
import { routes } from './app.routes';
import { asUser, probeWith } from './testing/session-testing';

describe('Khung trang (đầu trang và vùng lỗi phiên)', () => {
  const assign = jest.fn();

  beforeEach(() => {
    assign.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter(routes),
        { provide: PAGE_NAVIGATION, useValue: { assign } },
      ],
    });
  });

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  function render() {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    return fixture;
  }

  const text = (el: HTMLElement, id: string) => el.querySelector(`[data-testid="${id}"]`)?.textContent?.trim();

  it('khách: đầu trang có liên kết Đăng ký và Đăng nhập', () => {
    probeWith({ status: 401 });
    const el = render().nativeElement as HTMLElement;

    expect(el.querySelector('mat-toolbar a[href="/register"]')).not.toBeNull();
    expect(el.querySelector('mat-toolbar a[href="/login"]')).not.toBeNull();
  });

  it('người dùng: đầu trang không còn liên kết Đăng ký, Đăng nhập', () => {
    probeWith(asUser('USER'));
    const el = render().nativeElement as HTMLElement;

    expect(el.querySelector('mat-toolbar a[href="/register"]')).toBeNull();
    expect(el.querySelector('mat-toolbar a[href="/login"]')).toBeNull();
  });

  it('khách: không có nhãn vai, không có nút Đăng xuất', () => {
    probeWith({ status: 401 });
    const el = render().nativeElement as HTMLElement;

    expect(text(el, 'role-label')).toBeUndefined();
    expect(el.querySelector('[data-testid="logout"]')).toBeNull();
  });

  it('người dùng: nhãn "Người dùng" và nút Đăng xuất', () => {
    probeWith(asUser('USER'));
    const el = render().nativeElement as HTMLElement;

    expect(text(el, 'role-label')).toBe('Người dùng');
    expect(text(el, 'logout')).toBe('Đăng xuất');
  });

  it('quản trị: nhãn "Quản trị"', () => {
    probeWith(asUser('ADMIN'));

    expect(text(render().nativeElement, 'role-label')).toBe('Quản trị');
  });

  describe('nút Đăng xuất (KT-08)', () => {
    const LOGOUT_URL = '/bff/auth/logout';
    const click = (el: HTMLElement) => (el.querySelector('[data-testid="logout"]') as HTMLButtonElement).click();

    it('nút bấm được; bấm thì gửi POST /bff/auth/logout', () => {
      probeWith(asUser('USER'));
      const fixture = render();
      const button = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="logout"]') as HTMLButtonElement;

      expect(button.disabled).toBe(false);
      button.click();

      expect(TestBed.inject(HttpTestingController).expectOne(LOGOUT_URL).request.method).toBe('POST');
    });

    it('202: điều hướng tới Location', () => {
      probeWith(asUser('USER'));
      const fixture = render();
      click(fixture.nativeElement);
      TestBed.inject(HttpTestingController)
        .expectOne(LOGOUT_URL)
        .flush(null, { status: 202, statusText: 'Accepted', headers: { Location: 'http://auth.localhost:8000/connect/logout' } });

      expect(assign).toHaveBeenCalledWith('http://auth.localhost:8000/connect/logout');
    });

    it('đang gửi: nút bị khóa, bấm lần hai không gửi thêm yêu cầu', () => {
      probeWith(asUser('USER'));
      const fixture = render();
      const el = fixture.nativeElement as HTMLElement;
      click(el);
      fixture.detectChanges();
      click(el);

      expect((el.querySelector('[data-testid="logout"]') as HTMLButtonElement).disabled).toBe(true);
      TestBed.inject(HttpTestingController).expectOne(LOGOUT_URL); // đúng một yêu cầu
    });

    it('lỗi: báo lỗi, nút mở lại, vẫn là người đã đăng nhập (R8, R9)', () => {
      probeWith(asUser('USER'));
      const fixture = render();
      const el = fixture.nativeElement as HTMLElement;
      click(el);
      TestBed.inject(HttpTestingController).expectOne(LOGOUT_URL).flush({}, { status: 503, statusText: 'Unavailable' });
      fixture.detectChanges();

      expect(text(el, 'logout-error')).toContain('Không đăng xuất được');
      expect((el.querySelector('[data-testid="logout"]') as HTMLButtonElement).disabled).toBe(false);
      expect(text(el, 'role-label')).toBe('Người dùng');
      expect(assign).not.toHaveBeenCalled();
    });

    it('bấm lại sau lỗi: thử lại được và xóa thông báo cũ', () => {
      probeWith(asUser('USER'));
      const fixture = render();
      const el = fixture.nativeElement as HTMLElement;
      const http = TestBed.inject(HttpTestingController);
      click(el);
      http.expectOne(LOGOUT_URL).flush({}, { status: 503, statusText: 'Unavailable' });
      fixture.detectChanges();

      click(el);
      fixture.detectChanges();

      expect(el.querySelector('[data-testid="logout-error"]')).toBeNull();
      http.expectOne(LOGOUT_URL).flush(null, { status: 202, statusText: 'Accepted', headers: { Location: '/' } });
      expect(assign).toHaveBeenCalledWith('/');
    });
  });

  it('không xác định: hiện màn lỗi kèm nút thử lại, KHÔNG hiện vai hay nút Đăng xuất', () => {
    probeWith({ status: 503 });
    const el = render().nativeElement as HTMLElement;

    expect(text(el, 'session-error')).toContain('Thử lại');
    expect(text(el, 'role-label')).toBeUndefined();
    expect(el.querySelector('[data-testid="logout"]')).toBeNull();
  });

  it('không xác định: không đi đăng nhập dù mở trang cần đăng nhập (lỗi không phải khách, R10)', async () => {
    probeWith({ status: 503 });
    render();
    await RouterTestingHarness.create('/account');

    expect(assign).not.toHaveBeenCalled();
  });

  it('thử lại thành công: màn lỗi biến mất và nhãn vai hiện ra', async () => {
    probeWith({ status: 503 });
    const fixture = render();
    const el = fixture.nativeElement as HTMLElement;

    (el.querySelector('[data-testid="retry"]') as HTMLButtonElement).click();
    TestBed.inject(HttpTestingController).expectOne(SESSION_URL).flush({ userId: 'u-123', role: 'USER' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(el.querySelector('[data-testid="session-error"]')).toBeNull();
    expect(text(el, 'role-label')).toBe('Người dùng');
  });

  it('thử lại thành công: đi tiếp tới địa chỉ người dùng đã mở (guard đã chặn lúc không xác định)', async () => {
    probeWith({ status: 503 });
    const fixture = render();
    await TestBed.inject(Router).navigateByUrl('/account'); // guard hủy vì phiên không xác định
    expect(TestBed.inject(Router).url).not.toBe('/account');

    ((fixture.nativeElement as HTMLElement).querySelector('[data-testid="retry"]') as HTMLButtonElement).click();
    TestBed.inject(HttpTestingController).expectOne(SESSION_URL).flush({ userId: 'u-123', role: 'USER' });
    await fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/account');
  });
});
