import {Injectable, inject, signal} from '@angular/core';
import {Observable, map, catchError, of, tap} from 'rxjs';
import {toApiError} from '@t3n/http';
import {SellerProfile} from '../model/seller';
import {SellerApiService} from './seller-api.service';

/**
 * Trạng thái hồ sơ người bán, ba nghĩa với người dùng giống `SessionState`.
 * `unknown` là lỗi hoặc phản hồi sai khuôn: KHÔNG được coi là `none` (chưa là người bán), vì sẽ đẩy người đã có hồ sơ đi nộp lại (R3).
 */
export type SellerProfileState =
  | { kind: 'loading' }
  | { kind: 'none' }
  | { kind: 'pending' }
  | { kind: 'approved' }
  | { kind: 'rejected'; reason: string | null }
  | { kind: 'unknown' };

/** Trạng thái suy từ hồ sơ đã qua kiểm `status`. `rejectionReason` chỉ lấy khi là chuỗi. */
function stateOf(profile: SellerProfile): SellerProfileState {
  switch (profile.status) {
    case 'PENDING_REVIEW': return { kind: 'pending' };
    case 'APPROVED': return { kind: 'approved' };
    case 'REJECTED': return { kind: 'rejected', reason: typeof profile.rejectionReason === 'string' ? profile.rejectionReason : null };
  }
}

/** Chỉ 404 kèm đúng mã nghiệp vụ mới là "chưa có hồ sơ"; mọi lỗi khác là không xác định (R2, R3). */
function stateFromError(error: unknown): SellerProfileState {
  const apiError = toApiError(error);
  const notFound = apiError.status === 404 && apiError.code === 'SELLER_PROFILE_NOT_FOUND';
  return notFound ? { kind: 'none' } : { kind: 'unknown' };
}

/**
 * Trạng thái hồ sơ người bán dùng chung (guard, menu). Chỉ giữ `state`; hồ sơ đầy đủ do component hồ sơ tự giữ.
 */
@Injectable({ providedIn: 'root' })
export class SellerStatusService {
  private readonly api = inject(SellerApiService);

  private readonly _state = signal<SellerProfileState>({kind: 'loading'});
  readonly state = this._state.asReadonly();

  /**
   * Gọi API hồ sơ, trả đủ hồ sơ cho component render, đồng thời cập nhật `state` từ chính phản hồi đó
   * (thành công: theo `status`; lỗi: `none` hoặc `unknown`). Trong lúc chờ `state` giữ nguyên.
   * Vẫn phát lỗi cho component để hiện cảnh báo và nút thử lại; lúc đó `state` đã được đặt đúng.
   */
  loadProfileStatus(): Observable<SellerProfile> {
    return this.api.getMyProfile().pipe(
      tap({
        next: (profile) => this._state.set(stateOf(profile)),
        error: (error: unknown) => this._state.set(stateFromError(error)),
      }),
    );
  }

  /**
   * Chỉ làm mới `state`, bỏ hồ sơ và nuốt lỗi (lỗi đã nằm trong `state`). Luồng luôn hoàn tất bình thường.
   * Dùng ở bộ khởi động, sau khi nộp hoặc sửa, và nơi không cần hồ sơ đầy đủ.
   */
  refresh(): Observable<void> {
    return this.loadProfileStatus().pipe(
      map(() => undefined),
      catchError(() => of(undefined)),
    );
  }
}
