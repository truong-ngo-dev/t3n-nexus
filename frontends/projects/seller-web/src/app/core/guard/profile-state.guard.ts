import {CanActivateFn, Router} from '@angular/router';
import {inject} from '@angular/core';
import {SellerProfileState, SellerStatusService} from '../service/seller-profile/seller-status.service';

/**
 * Guard theo trạng thái hồ sơ: chỉ cho vào khi `allowed(state)` đúng, ngược lại về `/`.
 * `loading` và `unknown` trả `false` (không vào, không chuyển trang): khung ứng dụng đã hiện màn chờ hoặc nút Thử lại,
 * và Thử lại sẽ yêu cầu router vào lại URL hiện tại.
 */
function profileGuard(allowed: (state: SellerProfileState) => boolean): CanActivateFn {
  return () => {
    const state = inject(SellerStatusService).state();
    if (state.kind === 'loading' || state.kind === 'unknown') return false;
    return allowed(state) ? true : inject(Router).createUrlTree(['/']);
  };
}

/** `/profile/new`: chỉ khi chưa có hồ sơ. */
export const canSubmitProfileGuard: CanActivateFn = profileGuard((s) => s.kind === 'none');

/** `/profile/edit`: sửa toàn bộ, chỉ khi chờ duyệt. */
export const canEditPendingGuard: CanActivateFn = profileGuard((s) => s.kind === 'pending');

/** `/profile/resubmit`: sửa và nộp lại, chỉ khi bị từ chối. */
export const canResubmitProfileGuard: CanActivateFn = profileGuard((s) => s.kind === 'rejected');

/** `/profile/edit-shop`: sửa thông tin gian hàng, chỉ khi đã duyệt. */
export const canEditApprovedGuard: CanActivateFn = profileGuard((s) => s.kind === 'approved');

/** `/profile`: khi đã có hồ sơ (bất kể trạng thái). */
export const hasProfileGuard: CanActivateFn = profileGuard((s) => s.kind !== 'none');
