import {Injectable, inject} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {expectSuccess, unwrapData} from '@t3n/http';
import {SellerProfile, parseSellerProfile, SubmitSellerProfileRequest} from '../model/seller';

export const SELLER_PROFILE_URL = '/api/seller/seller/profile';
export const SELLER_PROFILE_RESUBMIT_URL = `${SELLER_PROFILE_URL}/resubmit`;

/**
 * Lớp bọc HTTP của hồ sơ người bán: gắn địa chỉ, bóc `ApiResponse`, kiểm `status`. Không giữ trạng thái.
 * Lỗi cứ phát ra (HTTP, `InvalidApiResponse`, `TimeoutError`); việc hiểu lỗi là của nơi gọi
 * (xem `classifyFailure` ở `@t3n/http`).
 */
@Injectable({ providedIn: 'root' })
export class SellerApiService {
  private readonly http = inject(HttpClient);

  /**
   * `GET /api/seller/profile` qua BFF: trả đủ hồ sơ để render. Thân sai khung hoặc `status` lạ phát `InvalidApiResponse`;
   * quá hạn chờ phát `TimeoutError`. Là lệnh đọc nên nơi gọi cho thử lại được.
   */
  getMyProfile(): Observable<SellerProfile> {
    return this.http.get<unknown>(SELLER_PROFILE_URL).pipe(unwrapData(parseSellerProfile));
  }

  /**
   * `POST /api/seller/profile` qua BFF: nộp hồ sơ lần đầu. Chỉ báo thành công hoặc lỗi (`Observable<void>`): thân phản hồi không được
   * dùng vì sau khi nộp, trạng thái được tải lại bằng GET.
   *
   * Vẫn kiểm thân có phải khung thành công (`expectSuccess`) để "2xx mà success:false" không lọt thành công, và có hạn chờ
   * (`requestTimeout`) để backend treo không làm nút gửi khóa mãi. Lỗi `InvalidApiResponse` và `TimeoutError` đều nghĩa là
   * **kết quả chưa rõ**: hồ sơ có thể đã được tạo, nên nơi gọi phải kiểm lại trạng thái trước khi cho gửi lại.
   */
  submitSellerProfile(request: SubmitSellerProfileRequest): Observable<void> {
    return this.http.post<unknown>(SELLER_PROFILE_URL, request).pipe(expectSuccess());
  }

  /**
   * `POST /api/seller/profile/resubmit` qua BFF: sửa và nộp lại hồ sơ bị từ chối, gửi **toàn bộ** trường. Cùng hợp đồng phản hồi
   * và cùng cách xử lý lỗi với {@link submitSellerProfile}.
   */
  resubmitSellerProfile(request: SubmitSellerProfileRequest): Observable<void> {
    return this.http.post<unknown>(SELLER_PROFILE_RESUBMIT_URL, request).pipe(expectSuccess());
  }
}
