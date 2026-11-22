import {Injectable, inject} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {ignoreBody, requestTimeout} from '@t3n/http';
import {
  SELLER_PROFILE_RESUBMIT_URL,
  SELLER_PROFILE_SHOP_INFO_URL,
  SELLER_PROFILE_URL,
} from '../../../core/service/seller-profile/seller-profile.api';
import {SubmitSellerProfileRequest, UpdateShopInfoRequest} from './seller-profile-command.model';

/**
 * Lệnh ghi hồ sơ người bán của tôi: nộp, sửa, nộp lại, sửa thông tin gian hàng. Chỉ tính năng hồ sơ dùng nên nằm ở đây;
 * lệnh đọc dùng chung ở `core` (`SellerProfileApi`), địa chỉ lấy từ đó.
 *
 * Mọi lệnh chỉ báo thành công hoặc lỗi (`Observable<void>`, không đọc thân): sau khi ghi, trạng thái hoặc trang xem tải lại hồ sơ.
 * `TimeoutError` (hoặc `HttpErrorResponse` mang mã 2xx khi thân không đọc được) nghĩa là **kết quả chưa rõ**: thao tác có thể đã ghi,
 * nên nơi gọi phải kiểm lại trạng thái trước khi cho gửi lại.
 */
@Injectable({ providedIn: 'root' })
export class SellerProfileCommandApi {
  private readonly http = inject(HttpClient);

  /** `POST /profile`: nộp hồ sơ lần đầu. */
  submit(request: SubmitSellerProfileRequest): Observable<void> {
    return this.http.post<unknown>(SELLER_PROFILE_URL, request).pipe(requestTimeout(), ignoreBody());
  }

  /** `POST /profile/resubmit`: sửa và nộp lại hồ sơ bị từ chối, gửi **toàn bộ** trường. */
  resubmit(request: SubmitSellerProfileRequest): Observable<void> {
    return this.http.post<unknown>(SELLER_PROFILE_RESUBMIT_URL, request).pipe(requestTimeout(), ignoreBody());
  }

  /**
   * `PUT /profile`: thay toàn bộ hồ sơ khi chờ duyệt, trong một giao dịch, không đổi trạng thái.
   * **Backend chưa có** (`1-seller-profile/srs.md` mục 7, B1): tới khi có, lời gọi nhận 4xx và biểu mẫu hiện lỗi chung.
   */
  update(request: SubmitSellerProfileRequest): Observable<void> {
    return this.http.put<unknown>(SELLER_PROFILE_URL, request).pipe(requestTimeout(), ignoreBody());
  }

  /** `PATCH /profile/shop-info`: sửa thông tin gian hàng khi đã duyệt, chỉ mang ô đã đổi. */
  updateShopInfo(patch: UpdateShopInfoRequest): Observable<void> {
    return this.http.patch<unknown>(SELLER_PROFILE_SHOP_INFO_URL, patch).pipe(requestTimeout(), ignoreBody());
  }
}
