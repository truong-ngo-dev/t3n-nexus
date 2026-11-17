import {Injectable, inject} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable, map} from 'rxjs';
import {SellerProfile, parseSellerProfile} from '../model/seller';

export const SELLER_PROFILE_URL = '/api/seller/seller/profile';

/** 200 nhưng thân phản hồi sai khuôn. */
export class InvalidSellerProfileResponse extends Error {
  constructor() {
    super('Phản hồi hồ sơ người bán sai khuôn');
  }
}

/**
 * Lớp bọc HTTP của hồ sơ người bán: gắn địa chỉ, bóc `ApiResponse`, kiểm `status`. Không giữ trạng thái.
 * Lỗi cứ phát ra (HTTP hoặc `InvalidSellerProfileResponse`); việc hiểu lỗi là của nơi gọi.
 */
@Injectable({ providedIn: 'root' })
export class SellerApiService {
  private readonly http = inject(HttpClient);

  /** `GET /api/seller/profile` qua BFF: trả đủ hồ sơ để render. */
  getMyProfile(): Observable<SellerProfile> {
    return this.http.get<unknown>(SELLER_PROFILE_URL).pipe(
      map((body) => {
        const profile = parseSellerProfile(body);
        if (!profile) throw new InvalidSellerProfileResponse();
        return profile;
      }),
    );
  }
}
