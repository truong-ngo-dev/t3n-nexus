import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { LOGIN_URL, PAGE_NAVIGATION } from '@t3n/auth';
import { LoginComponent } from './login.component';

describe('LoginComponent (KT-14)', () => {
  const assign = jest.fn();

  beforeEach(() => {
    assign.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'login', component: LoginComponent }]),
        { provide: PAGE_NAVIGATION, useValue: { assign } },
      ],
    });
  });

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  async function open(url: string) {
    const harness = await RouterTestingHarness.create(url);
    const el = harness.routeNativeElement as HTMLElement;
    return { el, q: (id: string) => el.querySelector(`[data-testid="${id}"]`) };
  }

  it('có nút đăng nhập trỏ tới /bff/auth/login (điều hướng cấp cao, không phải lời gọi fetch)', async () => {
    const { q } = await open('/login');

    expect(q('login')?.getAttribute('href')).toBe(LOGIN_URL);
    expect(LOGIN_URL).toBe('/bff/auth/login');
  });

  it('không có ?error: không hiện thông báo lỗi', async () => {
    const { q } = await open('/login');

    expect(q('login-error')).toBeNull();
  });

  it('?error: hiện một thông báo chung và vẫn có nút thử lại', async () => {
    const { q } = await open('/login?error');

    expect(q('login-error')?.textContent).toContain('Đăng nhập không thành công');
    expect(q('login')?.getAttribute('href')).toBe(LOGIN_URL);
  });

  it('?error: KHÔNG tự chuyển sang đăng nhập và không gọi mạng (tránh vòng lặp, R5)', async () => {
    await open('/login?error');

    expect(assign).not.toHaveBeenCalled();
    TestBed.inject(HttpTestingController).expectNone(() => true);
  });

  it('?error có giá trị: vẫn thông báo chung, không in giá trị ra (không đoán lý do)', async () => {
    const { el } = await open('/login?error=SECRET_REASON');

    expect(el.textContent).toContain('Đăng nhập không thành công');
    expect(el.textContent).not.toContain('SECRET_REASON');
  });
});
