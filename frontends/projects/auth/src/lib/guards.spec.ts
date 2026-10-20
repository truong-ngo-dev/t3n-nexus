import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, RouterStateSnapshot, UrlTree } from '@angular/router';
import { Role } from '@t3n/model';
import { authenticatedGuard, guestOnlyGuard, LOGIN_URL, PAGE_NAVIGATION, SessionService, SessionState } from '@t3n/auth';

// KT-03
describe('guards (KT-03)', () => {
  const assign = jest.fn();
  const state = signal<SessionState>({ kind: 'loading' });

  const authenticated = (role: Role): SessionState => ({ kind: 'authenticated', session: { userId: 'u1', role } });

  beforeEach(() => {
    assign.mockReset();
    state.set({ kind: 'loading' });
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: SessionService, useValue: { state: state.asReadonly() } },
        { provide: PAGE_NAVIGATION, useValue: { assign } },
      ],
    });
  });

  const route = (data: Record<string, unknown> = {}) => ({ data }) as unknown as ActivatedRouteSnapshot;
  const routerState = {} as RouterStateSnapshot;
  const run = (guard: typeof authenticatedGuard, data?: Record<string, unknown>) =>
    TestBed.runInInjectionContext(() => guard(route(data), routerState));

  describe('authenticatedGuard (trang cần đăng nhập)', () => {
    it('đã đăng nhập thì vào', () => {
      state.set(authenticated('USER'));
      expect(run(authenticatedGuard)).toBe(true);
      expect(assign).not.toHaveBeenCalled();
    });

    it('chưa đăng nhập thì đi đăng nhập đúng một lần bằng điều hướng cấp cao, và không vào', () => {
      state.set({ kind: 'anonymous' });
      expect(run(authenticatedGuard)).toBe(false);
      expect(assign).toHaveBeenCalledTimes(1);
      expect(assign).toHaveBeenCalledWith(LOGIN_URL);
      expect(LOGIN_URL).toBe('/bff/auth/login');
    });

    it.each<SessionState>([{ kind: 'loading' }, { kind: 'unknown' }])(
      'trạng thái %j: không vào và KHÔNG đi đăng nhập (không coi lỗi là khách)',
      (s) => {
        state.set(s);
        expect(run(authenticatedGuard)).toBe(false);
        expect(assign).not.toHaveBeenCalled();
      },
    );

    it('đủ vai thì vào', () => {
      state.set(authenticated('ADMIN'));
      expect(run(authenticatedGuard, { roles: ['ADMIN'] })).toBe(true);
    });

    it('không đủ vai thì không vào, không đi đăng nhập (tránh vòng lặp)', () => {
      state.set(authenticated('USER'));
      const result = run(authenticatedGuard, { roles: ['ADMIN'] });
      expect(result).toBeInstanceOf(UrlTree);
      expect(String(result)).toBe('/');
      expect(assign).not.toHaveBeenCalled();
    });
  });

  describe('guestOnlyGuard (/login, /register)', () => {
    it('khách thì vào', () => {
      state.set({ kind: 'anonymous' });
      expect(run(guestOnlyGuard)).toBe(true);
    });

    it.each<Role>(['USER', 'ADMIN'])('đã đăng nhập (%s) thì về /account, không đi đăng nhập', (role) => {
      state.set(authenticated(role));
      const result = run(guestOnlyGuard);
      expect(result).toBeInstanceOf(UrlTree);
      expect(String(result)).toBe('/account');
      expect(assign).not.toHaveBeenCalled();
    });

    it('không xác định thì cho vào (không đẩy đi đâu)', () => {
      state.set({ kind: 'unknown' });
      expect(run(guestOnlyGuard)).toBe(true);
    });
  });

  it('không vòng lặp (R1): người đã đăng nhập vào /login rồi tới /account thì vào được', () => {
    state.set(authenticated('USER'));
    expect(String(run(guestOnlyGuard))).toBe('/account');
    expect(run(authenticatedGuard)).toBe(true);
    expect(assign).not.toHaveBeenCalled();
  });
});
