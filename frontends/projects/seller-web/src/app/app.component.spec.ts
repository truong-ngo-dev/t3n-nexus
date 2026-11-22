import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { LogoutResult, SessionService, SessionState } from '@t3n/auth';
import { Subject } from 'rxjs';
import { AppComponent } from './app.component';
import { SellerProfileState, SellerStatusService } from './core/service/seller-profile/seller-status.service';

/*
 * KT-04 (khung). Kiểm LỚP `AppComponent` (signal và phương thức), không kiểm mẫu HTML (tech.md T7).
 * Việc nút thật sự bị khóa khi `loggingOut()` hoặc `retrying()` đúng sẽ kiểm bằng mắt ở 7.10.
 *
 * Các dịch vụ được giả bằng Subject: test tự quyết lúc nào máy chủ "trả lời", nên mô phỏng được trạng thái "đang chờ".
 *   - session / seller: tín hiệu giả của hai dịch vụ trạng thái; test đặt giá trị để dựng tình huống.
 *   - probe: thăm dò phiên giả. Dịch vụ thật đặt `state` rồi hoàn tất; ở đây test tự đặt `session` trước khi hoàn tất.
 *   - navigate: theo dõi `Router.navigateByUrl`. Lần điều hướng đầu bị `sellerGuard` hủy khi phiên/hồ sơ lỗi và router
 *     không tự thử lại, nên thử lại xong mà không yêu cầu điều hướng lại thì `<router-outlet />` trống dù đã hết lỗi.
 *     Spec ràng buộc cách làm là gọi `Router.navigateByUrl`.
 */
describe('AppComponent (KT-04)', () => {
  const session = signal<SessionState>({ kind: 'authenticated', session: { userId: 'u1', role: 'USER' } });
  const seller = signal<SellerProfileState>({ kind: 'approved' });

  let logoutResponse: Subject<LogoutResult>;
  let probeResponse: Subject<void>;
  let refreshResponse: Subject<void>;
  let logout: jest.Mock;
  let probe: jest.Mock;
  let refresh: jest.Mock;
  let navigate: jest.SpyInstance;
  let component: AppComponent;

  /** Dịch vụ thật phát một giá trị rồi hoàn tất. */
  const finish = (subject: Subject<void>) => {
    subject.next();
    subject.complete();
  };

  beforeEach(() => {
    session.set({ kind: 'authenticated', session: { userId: 'u1', role: 'USER' } });
    seller.set({ kind: 'approved' });
    logoutResponse = new Subject<LogoutResult>();
    probeResponse = new Subject<void>();
    refreshResponse = new Subject<void>();
    logout = jest.fn(() => logoutResponse);
    probe = jest.fn(() => probeResponse);
    refresh = jest.fn(() => refreshResponse);

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: SessionService, useValue: { state: session.asReadonly(), logout, probe } },
        { provide: SellerStatusService, useValue: { state: seller.asReadonly(), refresh } },
      ],
    });
    navigate = jest.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    component = TestBed.createComponent(AppComponent).componentInstance;
  });

  describe('đăng xuất', () => {
    it('lúc đầu: không đang gửi, không có lỗi', () => {
      expect(component.loggingOut()).toBe(false);
      expect(component.logoutError()).toBeNull();
      expect(logout).not.toHaveBeenCalled();
    });

    it('bấm đăng xuất: gọi dịch vụ một lần và chuyển sang đang gửi', () => {
      component.logout();

      expect(logout).toHaveBeenCalledTimes(1);
      expect(component.loggingOut()).toBe(true);
    });

    it('bấm lần nữa khi đang gửi: không gọi dịch vụ thêm (bấm đúp)', () => {
      component.logout();
      component.logout();
      component.logout();

      expect(logout).toHaveBeenCalledTimes(1);
    });

    it('thành công (redirected): không báo lỗi, vẫn khóa vì trang sắp rời đi', () => {
      component.logout();
      logoutResponse.next({ kind: 'redirected' });

      expect(component.logoutError()).toBeNull();
      expect(component.loggingOut()).toBe(true);
    });

    it('thất bại (failed): có thông báo lỗi và mở khóa lại', () => {
      component.logout();
      logoutResponse.next({ kind: 'failed' });

      expect(component.logoutError()).toEqual(expect.any(String));
      expect(component.logoutError()).not.toBe('');
      expect(component.loggingOut()).toBe(false);
    });

    it('sau thất bại bấm lại: gọi lại dịch vụ và xóa thông báo lỗi cũ trong lúc chờ', () => {
      component.logout();
      logoutResponse.next({ kind: 'failed' });

      logoutResponse = new Subject<LogoutResult>(); // lần gọi thứ hai nhận một luồng mới
      component.logout();

      expect(logout).toHaveBeenCalledTimes(2);
      expect(component.logoutError()).toBeNull();
      expect(component.loggingOut()).toBe(true);
    });
  });

  /*
   * `checkScreen()` quyết định vùng nội dung hiện gì: 'loading' (vòng quay), 'error' (thông báo + nút thử lại) hay 'ok' (`<router-outlet />`).
   * Hồ sơ người bán CHỈ được xét khi phiên là USER: bộ khởi động chỉ nạp hồ sơ cho USER, nên với khách/ADMIN
   * `SellerStatusService.state` đứng yên ở 'loading' (giá trị đầu); nếu xét nhầm thì họ bị kẹt ở màn đang tải mãi.
   */
  describe('màn hiện ở vùng nội dung (checkScreen)', () => {
    type Screen = 'loading' | 'error' | 'ok';
    const user: SessionState = { kind: 'authenticated', session: { userId: 'u1', role: 'USER' } };
    const admin: SessionState = { kind: 'authenticated', session: { userId: 'a1', role: 'ADMIN' } };

    it.each<[string, SessionState, SellerProfileState, Screen]>([
      // phiên chưa trả lời hoặc lỗi: hồ sơ không có nghĩa
      ['phiên đang kiểm tra', { kind: 'loading' }, { kind: 'loading' }, 'loading'],
      ['phiên lỗi', { kind: 'unknown' }, { kind: 'loading' }, 'error'],
      // khách và ADMIN: hồ sơ không được nạp, phải bỏ qua dù đang 'loading'
      ['khách (hồ sơ chưa nạp)', { kind: 'anonymous' }, { kind: 'loading' }, 'ok'],
      ['ADMIN (hồ sơ chưa nạp)', admin, { kind: 'loading' }, 'ok'],
      // USER: xét thêm hồ sơ
      ['USER, hồ sơ đang nạp', user, { kind: 'loading' }, 'loading'],
      ['USER, hồ sơ lỗi', user, { kind: 'unknown' }, 'error'],
      ['USER, chưa là người bán', user, { kind: 'none' }, 'ok'],
      ['USER, chờ duyệt', user, { kind: 'pending' }, 'ok'],
      ['USER, đã duyệt', user, { kind: 'approved' }, 'ok'],
      ['USER, bị từ chối', user, { kind: 'rejected', reason: 'x' }, 'ok'],
    ])('%s', (_name, sessionState, sellerState, expected) => {
      session.set(sessionState);
      seller.set(sellerState);

      expect(component.checkScreen()).toBe(expected);
    });

    it('đổi trạng thái thì checkScreen cập nhật theo (computed, không phải giá trị chụp một lần)', () => {
      session.set(user);
      seller.set({ kind: 'unknown' });
      expect(component.checkScreen()).toBe('error');

      seller.set({ kind: 'approved' });
      expect(component.checkScreen()).toBe('ok');
    });
  });

  describe('thử lại (màn lỗi ở khung)', () => {
    it('lúc đầu: không đang thử lại', () => {
      expect(component.retrying()).toBe(false);
    });

    describe('phiên lỗi (unknown)', () => {
      beforeEach(() => session.set({ kind: 'unknown' }));

      it('thăm dò phiên lại, chưa nạp hồ sơ khi phiên chưa trả lời, và đang thử lại', () => {
        component.retry();

        expect(probe).toHaveBeenCalledTimes(1);
        expect(refresh).not.toHaveBeenCalled();
        expect(component.retrying()).toBe(true);
      });

      it('thăm dò ra USER: nạp tiếp hồ sơ, và chỉ hết thử lại khi hồ sơ xong', () => {
        component.retry();
        session.set({ kind: 'authenticated', session: { userId: 'u1', role: 'USER' } });
        finish(probeResponse);

        expect(probe).toHaveBeenCalledTimes(1); // phải thăm dò phiên trước, không nhảy thẳng sang hồ sơ
        expect(refresh).toHaveBeenCalledTimes(1);
        expect(component.retrying()).toBe(true);
        expect(navigate).not.toHaveBeenCalled(); // hồ sơ chưa xong thì chưa điều hướng lại

        finish(refreshResponse);
        expect(component.retrying()).toBe(false);
        expect(navigate).toHaveBeenCalledTimes(1); // hết lỗi: yêu cầu router vào lại trang
        // Lần điều hướng đầu bị hủy nên URL của router vẫn là URL cũ: không có tùy chọn này router coi là cùng URL và bỏ qua (phát hiện ở Playwright).
        expect(navigate).toHaveBeenCalledWith(expect.any(String), { onSameUrlNavigation: 'reload' });
      });

      it.each<[string, SessionState, boolean]>([
        ['ADMIN', { kind: 'authenticated', session: { userId: 'a1', role: 'ADMIN' } }, true],
        ['khách', { kind: 'anonymous' }, true],
        ['vẫn lỗi', { kind: 'unknown' }, false],
      ])('thăm dò ra %s: không nạp hồ sơ và hết thử lại', (_name, result, navigates) => {
        component.retry();
        session.set(result);
        finish(probeResponse);

        expect(refresh).not.toHaveBeenCalled();
        expect(component.retrying()).toBe(false);
        // ADMIN và khách đã hết lỗi: để guard xử lý (ADMIN sang trang thông báo, khách đi đăng nhập). Còn lỗi thì ở lại màn lỗi.
        expect(navigate).toHaveBeenCalledTimes(navigates ? 1 : 0);
      });
    });

    describe('phiên ổn, hồ sơ lỗi (unknown)', () => {
      beforeEach(() => seller.set({ kind: 'unknown' }));

      it('chỉ nạp lại hồ sơ, không thăm dò phiên lại', () => {
        component.retry();

        expect(refresh).toHaveBeenCalledTimes(1);
        expect(probe).not.toHaveBeenCalled();
        expect(component.retrying()).toBe(true);
      });

      it('hồ sơ xong và ra trạng thái hợp lệ: hết thử lại và điều hướng lại một lần', () => {
        component.retry();
        seller.set({ kind: 'approved' }); // dịch vụ thật đặt state trước khi hoàn tất
        finish(refreshResponse);

        expect(component.retrying()).toBe(false);
        expect(navigate).toHaveBeenCalledTimes(1);
      });

      it('hồ sơ xong nhưng vẫn lỗi: hết thử lại, KHÔNG điều hướng (ở lại màn lỗi)', () => {
        component.retry();
        finish(refreshResponse); // seller vẫn là unknown

        expect(component.retrying()).toBe(false);
        expect(navigate).not.toHaveBeenCalled();
      });
    });

    it('bấm lần nữa khi đang thử lại: không gọi thêm (bấm đúp)', () => {
      seller.set({ kind: 'unknown' });
      component.retry();
      component.retry();
      component.retry();

      expect(refresh).toHaveBeenCalledTimes(1);
    });

    it('xong rồi vẫn bấm lại được', () => {
      seller.set({ kind: 'unknown' });
      component.retry();
      finish(refreshResponse);

      refreshResponse = new Subject<void>(); // lần gọi thứ hai nhận một luồng mới
      component.retry();

      expect(refresh).toHaveBeenCalledTimes(2);
      expect(component.retrying()).toBe(true);
    });
  });
});
