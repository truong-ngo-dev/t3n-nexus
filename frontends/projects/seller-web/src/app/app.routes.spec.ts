import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { LOGIN_URL, PAGE_NAVIGATION, SessionService, SessionState } from '@t3n/auth';
import { Role } from '@t3n/model';
import { routes } from './app.routes';
import { ForbiddenAccountComponent } from './core/pages/forbidden-account/forbidden-account.component';
import { HomeComponent } from './features/home/home.component';
import { LoginComponent } from './core/pages/login/login.component';
import { NotFoundComponent } from './core/pages/not-found/not-found.component';
import { SellerProfileState, SellerStatusService } from './core/service/seller-profile/seller-status.service';

/*
 * KT-02 (phần bảng route): dùng đúng `routes` của ứng dụng, chỉ giả phiên và điều hướng cấp cao.
 * Spec guard (core/guard) kiểm từng guard; spec này kiểm guard được GẮN ĐÚNG route và đường không khớp.
 * RouterTestingHarness: điều hướng thật tới một URL và trả về component đang hiện.
 */
describe('Bảng route của seller-web', () => {
  const assign = jest.fn();
  const session = signal<SessionState>({ kind: 'loading' });
  const authenticated = (role: Role): SessionState => ({ kind: 'authenticated', session: { userId: 'u1', role } });

  beforeEach(() => {
    assign.mockReset();
    session.set({ kind: 'loading' });
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        { provide: SessionService, useValue: { state: session.asReadonly() } },
        { provide: PAGE_NAVIGATION, useValue: { assign } },
        // trang chủ đọc trạng thái hồ sơ; spec này chỉ kiểm bảng route nên giữ trạng thái ở 'loading', không gọi API
        { provide: SellerStatusService, useValue: { state: signal<SellerProfileState>({ kind: 'loading' }).asReadonly() } },
      ],
    });
  });

  /** Điều hướng tới `url`, trả về URL cuối cùng của router. */
  const visit = async (url: string) => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    return { harness, url: TestBed.inject(Router).url };
  };

  describe('"/" (trang của người bán)', () => {
    it('USER thấy trang chủ', async () => {
      session.set(authenticated('USER'));
      const { harness, url } = await visit('/');
      expect(url).toBe('/');
      expect(harness.routeDebugElement?.componentInstance).toBeInstanceOf(HomeComponent);
    });

    it('ADMIN bị chuyển tới /forbidden-account và thấy trang thông báo', async () => {
      session.set(authenticated('ADMIN'));
      const { harness, url } = await visit('/');
      expect(url).toBe('/forbidden-account');
      expect(harness.routeDebugElement?.componentInstance).toBeInstanceOf(ForbiddenAccountComponent);
    });

    it('khách: đi đăng nhập một lần và không vào trang', async () => {
      session.set({ kind: 'anonymous' });
      const { harness } = await visit('/');
      expect(assign).toHaveBeenCalledTimes(1);
      expect(assign).toHaveBeenCalledWith(LOGIN_URL);
      expect(harness.routeDebugElement?.componentInstance).not.toBeInstanceOf(HomeComponent);
    });

    it.each<SessionState>([{ kind: 'loading' }, { kind: 'unknown' }])('trạng thái %j: không vào, không đi đăng nhập', async (state) => {
      session.set(state);
      const { harness } = await visit('/');
      expect(assign).not.toHaveBeenCalled();
      expect(harness.routeDebugElement?.componentInstance).not.toBeInstanceOf(HomeComponent);
    });
  });

  describe('"/login"', () => {
    it('khách thấy trang đăng nhập', async () => {
      session.set({ kind: 'anonymous' });
      const { harness, url } = await visit('/login');
      expect(url).toBe('/login');
      expect(harness.routeDebugElement?.componentInstance).toBeInstanceOf(LoginComponent);
    });

    it('USER đã đăng nhập thì về "/"', async () => {
      session.set(authenticated('USER'));
      const { url } = await visit('/login');
      expect(url).toBe('/');
    });
  });

  describe('"/forbidden-account"', () => {
    it('không có guard: ADMIN ở lại, không vòng lặp', async () => {
      session.set(authenticated('ADMIN'));
      const { harness, url } = await visit('/forbidden-account');
      expect(url).toBe('/forbidden-account');
      expect(harness.routeDebugElement?.componentInstance).toBeInstanceOf(ForbiddenAccountComponent);
    });
  });

  describe('đường không khớp ("**")', () => {
    it.each<SessionState>([authenticated('USER'), { kind: 'anonymous' }])(
      'hiện trang không tìm thấy, giữ nguyên URL, không chuyển hướng (phiên %j)',
      async (state) => {
        session.set(state);
        const { harness, url } = await visit('/khong-ton-tai');
        expect(url).toBe('/khong-ton-tai');
        expect(harness.routeDebugElement?.componentInstance).toBeInstanceOf(NotFoundComponent);
        expect(assign).not.toHaveBeenCalled();
      },
    );
  });
});
