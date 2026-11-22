import {Injectable, inject} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {requestTimeout, unwrapData} from '@t3n/http';
import {SellerProfile, parseSellerProfile} from './seller-profile.model';

/** Địa chỉ hồ sơ của tôi qua BFF. Lớp lệnh ghi ở tính năng hồ sơ dùng lại hằng này, để địa chỉ chỉ nằm một chỗ. */
export const SELLER_PROFILE_URL = '/api/seller/seller/profile';
export const SELLER_PROFILE_RESUBMIT_URL = `${SELLER_PROFILE_URL}/resubmit`;
export const SELLER_PROFILE_SHOP_INFO_URL = `${SELLER_PROFILE_URL}/shop-info`;

/**
 * Lệnh đọc hồ sơ người bán của tôi: gắn địa chỉ, bóc `ApiResponse`, có hạn chờ. Không giữ trạng thái.
 *
 * Nằm ở `core` vì có hai nơi cùng đọc: `SellerStatusService` lấy `status`, tính năng hồ sơ lấy cả hồ sơ.
 * Lệnh ghi chỉ tính năng hồ sơ dùng nên ở `features/seller-profile/data/` (`SellerProfileCommandApi`).
 * Lỗi cứ phát ra (HTTP, `InvalidApiResponse`, `TimeoutError`); việc hiểu lỗi là của nơi gọi.
 */
@Injectable({ providedIn: 'root' })
export class SellerProfileApi {
  private readonly http = inject(HttpClient);

  /** `GET /profile`: hồ sơ đầy đủ. Thân sai khung hoặc `status` lạ phát `InvalidApiResponse`. Là lệnh đọc nên cho thử lại được. */
  getMyProfile(): Observable<SellerProfile> {
    return this.http.get<unknown>(SELLER_PROFILE_URL).pipe(requestTimeout(), unwrapData(parseSellerProfile));
  }
}
