import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { LOGIN_URL, PAGE_NAVIGATION, SessionService, SessionState } from '@t3n/auth';
import { Role } from '@t3n/model';
import { FORBIDDEN_ACCOUNT_URL, loginPageGuard, sellerGuard } from './seller.guard';

/*
 * KT-02 (flow.md mục 6; R1, R4, R5). Guard chỉ xét PHIÊN (vai), chưa xét trạng thái hồ sơ (việc của bước 7).
 *
 * Hai cách kiểm:
 *   - Gọi thẳng guard (hàm) trong ngữ cảnh inject: xem guard trả gì (true | false | UrlTree) và có gọi `assign` không.
 *   - Cho Router chạy thật với bảng route thu nhỏ: chứng minh KHÔNG vòng lặp (điều gọi thẳng guard không chứng minh được).
 */
@Component({ template: '' })
class Stub {}

describe('guard của seller-web (KT-02)', () => {
  const assign = jest.fn();
  const session = signal<SessionState>({ kind: 'loading' });

  const authenticated = (role: Role): SessionState => ({ kind: 'authenticated', session: { userId: 'u1', role } });

  const configure = (routes: Parameters<typeof provideRouter>[0] = []) => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        { provide: SessionService, useValue: { state: session.asReadonly() } }, // thay phiên thật bằng tín hiệu giả
        { provide: PAGE_NAVIGATION, useValue: { assign } },
      ],
    });
  };

  beforeEach(() => {
    assign.mockReset();
    session.set({ kind: 'loading' });
  });

  const run = (guard: typeof sellerGuard) =>
    TestBed.runInInjectionContext(() => guard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));

  /** Đường dẫn của UrlTree mà guard trả về (guard chuyển hướng bằng UrlTree, không gọi Router.navigate). */
  const pathOf = (result: unknown): string => {
    expect(result).toBeInstanceOf(UrlTree);
    return TestBed.inject(Router).serializeUrl(result as UrlTree);
  };

  describe('sellerGuard (trang của người bán)', () => {
    beforeEach(() => configure());

    it('USER thì vào', () => {
      session.set(authenticated('USER'));
      expect(run(sellerGuard)).toBe(true);
      expect(assign).not.toHaveBeenCalled();
    });

    it('khách thì không vào và đi đăng nhập đúng một lần bằng điều hướng cấp cao (R5)', () => {
      session.set({ kind: 'anonymous' });
      expect(run(sellerGuard)).toBe(false);
      expect(assign).toHaveBeenCalledTimes(1);
      expect(assign).toHaveBeenCalledWith(LOGIN_URL);
    });

    it('ADMIN thì không vào, được chuyển tới trang thông báo và KHÔNG đi đăng nhập (R4)', () => {
      session.set(authenticated('ADMIN'));
      expect(pathOf(run(sellerGuard))).toBe(FORBIDDEN_ACCOUNT_URL);
      expect(assign).not.toHaveBeenCalled();
    });

    it.each<SessionState>([{ kind: 'loading' }, { kind: 'unknown' }])(
      'trạng thái %j: không vào và KHÔNG đi đăng nhập (không coi lỗi là khách, R1)',
      (state) => {
        session.set(state);
        expect(run(sellerGuard)).toBe(false);
        expect(assign).not.toHaveBeenCalled();
      },
    );
  });

  describe('loginPageGuard (trang /login)', () => {
    beforeEach(() => configure());

    it('khách thì vào được, không điều hướng', () => {
      session.set({ kind: 'anonymous' });
      expect(run(loginPageGuard)).toBe(true);
      expect(assign).not.toHaveBeenCalled();
    });

    it.each<SessionState>([{ kind: 'loading' }, { kind: 'unknown' }])('trạng thái %j: vào được (trang có nút thử lại)', (state) => {
      session.set(state);
      expect(run(loginPageGuard)).toBe(true);
    });

    it.each<Role>(['USER', 'ADMIN'])('%s đã đăng nhập thì về "/" (không ở lại trang đăng nhập)', (role) => {
      session.set(authenticated(role));
      expect(pathOf(run(loginPageGuard))).toBe('/');
    });
  });

  describe('chạy qua Router thật (chứng minh không vòng lặp, R4)', () => {
    beforeEach(() =>
      configure([
        { path: '', canActivate: [sellerGuard], component: Stub },
        { path: 'login', canActivate: [loginPageGuard], component: Stub },
        { path: 'forbidden-account', component: Stub }, // KHÔNG gắn guard: là đích cuối của ADMIN
      ]),
    );

    const goTo = async (url: string): Promise<string> => {
      const router = TestBed.inject(Router);
      await router.navigateByUrl(url);
      return router.url;
    };

    it('ADMIN mở "/" thì dừng ở trang thông báo', async () => {
      session.set(authenticated('ADMIN'));
      expect(await goTo('/')).toBe(FORBIDDEN_ACCOUNT_URL);
    });

    it('ADMIN mở "/login" thì đi qua "/" rồi dừng ở trang thông báo', async () => {
      session.set(authenticated('ADMIN'));
      expect(await goTo('/login')).toBe(FORBIDDEN_ACCOUNT_URL);
    });

    it('ADMIN mở thẳng trang thông báo thì ở lại đó', async () => {
      session.set(authenticated('ADMIN'));
      expect(await goTo(FORBIDDEN_ACCOUNT_URL)).toBe(FORBIDDEN_ACCOUNT_URL);
    });

    it('USER mở "/login" thì về "/" và ở lại', async () => {
      session.set(authenticated('USER'));
      expect(await goTo('/login')).toBe('/');
    });
  });
});
