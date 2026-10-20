import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from '@t3n/model';
import { LOGIN_URL, PAGE_NAVIGATION } from './page-navigation';
import { SessionService } from './session.service';

/**
 * Trang cần đăng nhập. Đọc trạng thái đã có (bộ khởi động chờ thăm dò xong trước khi định tuyến, T3).
 * - Đã đăng nhập → vào. Nếu route khai `data: { roles: [...] }` mà vai không nằm trong đó → về `/`
 *   (không đi đăng nhập: đã có phiên, đi nữa sẽ vòng lặp, R1).
 * - Khách → điều hướng cấp cao tới đăng nhập đúng một lần, không vào.
 * - Đang kiểm hoặc không xác định → không vào và KHÔNG đi đăng nhập (không coi lỗi là khách, R10).
 */
export const authenticatedGuard: CanActivateFn = (route) => {
  const state = inject(SessionService).state();

  switch (state.kind) {
    case 'authenticated': {
      const roles = route.data['roles'] as Role[] | undefined;
      if (!roles || roles.includes(state.session.role)) return true;
      return inject(Router).createUrlTree(['/']);
    }
    case 'anonymous':
      inject(PAGE_NAVIGATION).assign(LOGIN_URL);
      return false;
    default:
      return false;
  }
};

/** `/login`, `/register`: người đã đăng nhập thì về `/account` (sửa vòng lặp R1). Khách và lỗi thì vào. */
export const guestOnlyGuard: CanActivateFn = () => {
  const state = inject(SessionService).state();
  return state.kind === 'authenticated' ? inject(Router).createUrlTree(['/account']) : true;
};
