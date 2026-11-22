/**
 * Xử lý **phản hồi của backend của ta** theo hai nhánh, cùng một file:
 * - **Nhánh 2xx (thành công)**: biến thân `unknown` thành kết quả đã kiểm, hoặc ném {@link InvalidApiResponse}
 *   ({@link unwrapData}, {@link ignoreBody}).
 * - **Nhánh lỗi** (cuối file): {@link classifyFailure} phân loại một lỗi (kể cả `InvalidApiResponse`, `TimeoutError`) để nơi gọi biết làm gì
 *   tiếp và thao tác đã ghi chưa; {@link requestTimeout} đặt hạn chờ cho lời gọi.
 *
 * Phần dưới đây mô tả nhánh 2xx.
 *
 * ## Vì sao cần
 * `HttpClient` coi 2xx là thành công và tham số kiểu của `get<T>()` chỉ là lời khẳng định với trình biên dịch, không kiểm gì lúc chạy.
 * Thân 2xx có thể là trang HTML của proxy hoặc `data` sai hình. Vì vậy ta gọi `http.<method><unknown>()` rồi kiểm tường minh trong
 * `pipe` phần ta thật sự dùng, thay vì tin kiểu hoặc bóc khung ngầm trong interceptor (kiểu sẽ nói dối, mất `code`/`traceId`).
 *
 * ## Không đánh giá "thành công" từ thân
 * Mã trạng thái HTTP là kết quả của yêu cầu (xem `docs/global/4.convention/api-response.md`): 2xx là thành công, mọi trạng thái khác
 * đã vào kênh lỗi của `HttpClient`. Thân phản hồi không được đọc lại để quyết định thành công hay thất bại (không `success`, không `code`).
 *
 * ## Hai cửa kiểm cho endpoint có dùng `data` (cửa nào trượt thì ném {@link InvalidApiResponse} với lý do tương ứng)
 * | Cửa | Điều kiện qua | Trượt thì |
 * |---|---|---|
 * | 1 | thân là một đối tượng (để đọc được `data`) | `not-envelope` |
 * | 2 | `data` qua hàm `parseData` của endpoint (trả khác `null`) | `data-mismatch` |
 *
 * Chỉ kiểm hình dạng ở mức đủ để đọc `data`; không đòi thân có `code` hay `success` (xem mục trên). Cửa 1 chạy trước nên `parseData` không
 * bao giờ nhận `data` của một thân không đọc được.
 *
 * ## Chọn handler theo endpoint (thư viện chỉ cấp hàm, **việc chọn là của người viết service**)
 * | Endpoint | Handler | Cửa kiểm | Kết quả |
 * |---|---|---|---|
 * | Ta **có dùng** `data` (kể cả không ràng buộc hình dạng) | {@link unwrapData} | 1, 2 | `T` |
 * | Ta **không dùng** thân (nộp, tạo, sửa, hoặc 204 không có thân) | {@link ignoreBody} | không | `void` |
 *
 * Không suy ra loại endpoint từ method HTTP: danh sách, tìm kiếm, xuất tệp vẫn dùng POST. Đọc hay ghi, gửi lại có an toàn không, là việc
 * của tính năng khi xử lý lỗi (xem {@link classifyFailure} ở cuối file), không phải của handler.
 *
 * ## Điều các handler KHÔNG làm
 * - Không đụng lỗi HTTP (4xx, 5xx, mất mạng): lỗi đi qua **nguyên vẹn** để `toErrorInfo` và `classifyFailure` đọc.
 * - Không ghi log và không hiển thị gì: nơi gọi bắt lỗi rồi quyết định. Không bao giờ hiển thị chữ nào từ thân.
 * - Không đặt hạn chờ: ghép thêm {@link requestTimeout} vào `pipe`.
 * - Không xử lý thân không phải JSON (`responseType: 'text' | 'blob'`, như xuất tệp): endpoint loại đó tự kiểm.
 *
 * ## Ví dụ dùng trong service
 * ```ts
 * // 1. Có dùng data: parseData quyết định mức chặt (xem ví dụ ở unwrapData)
 * getMyProfile(): Observable<SellerProfile> {
 *   return this.http.get<unknown>(URL).pipe(requestTimeout(), unwrapData(parseSellerProfile));
 * }
 *
 * // 2. Ghi, không dùng thân: 2xx là đủ
 * submitProfile(request: SubmitSellerProfileRequest): Observable<void> {
 *   return this.http.post<unknown>(URL, request).pipe(requestTimeout(), ignoreBody());
 * }
 * ```
 *
 * ## Ví dụ nơi gọi nhận kết quả
 * ```ts
 * this.api.submitProfile(request).subscribe({
 *   next: () => this.router.navigateByUrl('/profile'),
 *   error: (error: unknown) => {
 *     // InvalidApiResponse, TimeoutError và HttpErrorResponse đều vào đây; classifyFailure phân loại
 *     const view = mapSubmitError(error);
 *   },
 * });
 * ```
 *
 * @module
 */
import { HttpErrorResponse } from '@angular/common/http';
import { MonoTypeOperatorFunction, OperatorFunction, TimeoutError, map, timeout } from 'rxjs';
import { isRecord } from '@t3n/model';
import { toErrorInfo } from './api-error';

/**
 * Vì sao thân của một phản hồi 2xx không dùng được:
 * - `not-envelope`: thân không phải một đối tượng (rỗng, chuỗi, HTML, mảng) nên không đọc được `data`.
 * - `data-mismatch`: thân là đối tượng, nhưng `data` không có hoặc không đúng khuôn mà endpoint cần.
 */
export type InvalidApiResponseReason = 'not-envelope' | 'data-mismatch';

/**
 * Phản hồi có trạng thái 2xx nhưng thân không dùng được cho endpoint cần `data`. Đây là vi phạm hợp đồng của backend, không phải lỗi người
 * dùng gây ra: Angular coi 2xx là thành công nên lỗi này không tự vào kênh lỗi, phải được ném ra ở đây.
 *
 * Với thao tác đọc, coi là "không xác định, cho thử lại". Với thao tác ghi, kết quả **chưa rõ** (backend có thể đã ghi rồi mới
 * trả thân hỏng), nên phải kiểm lại trạng thái thật trước khi cho gửi lại. Không bao giờ hiển thị chữ nào từ thân cho người dùng.
 */
export class InvalidApiResponse extends Error {
  /** @param reason cửa kiểm mà phản hồi trượt; chỉ để ghi log và chẩn đoán, không để hiển thị */
  constructor(readonly reason: InvalidApiResponseReason) {
    super(`Phản hồi thành công nhưng thân không dùng được: ${reason}`);
    this.name = 'InvalidApiResponse';
  }
}

/**
 * Chuẩn hóa nhánh thành công của một phản hồi 2xx: lấy `data` từ thân và kiểm khuôn bằng `parseData`. Không đọc `success` hay `code`
 * để quyết định thành công: 2xx đã là thành công.
 *
 * @param body      thân nhận được, còn `unknown` (đừng khai `get<T>`: tham số kiểu của HttpClient không kiểm gì lúc chạy)
 * @param parseData hàm của từng endpoint: nhận `data`, trả giá trị đã thu hẹp kiểu, hoặc `null` nếu sai khuôn. Chỉ nên kiểm phần
 *                  quyết định luồng (ví dụ trạng thái hồ sơ); lỗi do chính hàm này ném ra được để nguyên vì là lỗi lập trình
 * @throws InvalidApiResponse `not-envelope` hoặc `data-mismatch`
 */
export function unwrapApiResponse<T>(body: unknown, parseData: (data: unknown) => T | null): T {
  if (!isRecord(body)) throw new InvalidApiResponse('not-envelope');
  const data = parseData(body['data']);
  if (data === null) throw new InvalidApiResponse('data-mismatch');
  return data;
}

/**
 * Handler cho endpoint **có dùng `data`**: dạng toán tử RxJS của {@link unwrapApiResponse}. Lỗi HTTP đi qua nguyên vẹn.
 *
 * Mức chặt của `data` do `parseData` quyết, ở nơi khai báo endpoint:
 * - **Chặt**: kiểm trường điều khiển luồng, sai thì trả `null` (phát `data-mismatch`).
 * - **Lỏng**: điền mặc định ngay trong `parseData` (`items ?? []`) để component không phải viết `res?.data?.x ?? []`.
 * - **Không kiểm**: `(data) => data as T` (hoặc `as unknown`), khi ta cần bóc `data` nhưng không ràng buộc hình dạng. Thân vẫn phải là đối tượng
 *   (`not-envelope` nếu không). Lưu ý `null` nghĩa là "sai khuôn", nên `data: null` hợp lệ cũng bị `data-mismatch`: bọc lại, ví dụ
 *   `(data) => ({ value: data })`.
 *
 * ```ts
 * // chặt: chỉ kiểm trường điều khiển luồng
 * const parseStatus = (data: unknown): { status: string } | null =>
 *   isRecord(data) && typeof data['status'] === 'string' ? { status: data['status'] } : null;
 * http.get<unknown>(url).pipe(unwrapData(parseStatus));
 *
 * // lỏng: thiếu thì coi như rỗng, đặt trong service
 * const parseItems = (data: unknown): string[] =>
 *   isRecord(data) && Array.isArray(data['items']) ? data['items'].filter((x): x is string => typeof x === 'string') : [];
 * http.get<unknown>(url).pipe(unwrapData(parseItems));
 * ```
 *
 * @param parseData hàm của endpoint, xem {@link unwrapApiResponse}
 */
export function unwrapData<T>(parseData: (data: unknown) => T | null): OperatorFunction<unknown, T> {
  return map((body) => unwrapApiResponse(body, parseData));
}

/**
 * Handler cho endpoint **không dùng thân** (nộp, tạo, sửa mà sau đó trạng thái được tải lại; hoặc 204 không có thân, ví dụ đăng xuất, xóa):
 * bỏ qua thân, chỉ báo thành công (`void`).
 *
 * Không cần kiểm gì thêm: `HttpClient` đã đẩy mọi trạng thái ngoài 2xx vào kênh lỗi, và thân không phải JSON (trang HTML của proxy)
 * làm `HttpClient` lỗi khi đọc, lỗi đó đến dưới dạng `HttpErrorResponse` mang mã 2xx và được `classifyFailure` xếp
 * `unknown-outcome`. Chỗ hở còn lại chỉ là proxy trả 200 với thân **rỗng**, rất hiếm.
 *
 * Chọn nhầm cho endpoint **có** `data` ta cần thì mất lớp kiểm khuôn; mỗi endpoint nên có một ca test "thành công" để bắt lỗi chọn nhầm.
 *
 * ```ts
 * this.http.post<unknown>(url, body).pipe(ignoreBody());
 * ```
 */
export function ignoreBody(): OperatorFunction<unknown, void> {
  return map(() => undefined);
}

// ====================== NHÁNH LỖI ======================

/**
 * Bản chất của một lỗi, theo câu hỏi nơi gọi cần trả lời: "tôi làm gì tiếp, và thao tác đã ghi chưa?".
 * - `handled`: bộ chặn chung đã lo (401 đã điều hướng đăng nhập); nơi gọi không làm gì thêm.
 * - `transient`: **chưa ghi gì**, an toàn để cho gửi lại (mạng, 429, 502, 503, 500, CSRF sau lần thử lại).
 * - `unknown-outcome`: **có thể ĐÃ ghi rồi** (504, hết giờ, thân 2xx không đáng tin); với thao tác ghi phải kiểm lại trạng thái thật
 *   trước khi cho gửi lại, nếu không dễ ghi trùng.
 * - `rejected`: máy chủ từ chối có chủ đích (4xx); tính năng tự quyết gắn vào ô hay hiện câu chung theo `code`.
 * - `unexpected`: lưới đỡ (lỗi lập trình, chuyển hướng lạ).
 */
export type FailureClass = 'handled' | 'transient' | 'unknown-outcome' | 'rejected' | 'unexpected';

/**
 * Phân loại một lỗi thành {@link FailureClass}. Hàm thuần, dùng chung cho mọi tính năng, để bảng "502 hay 504 thì làm gì" nằm một chỗ.
 * Chỉ đọc trạng thái, loại lỗi và (qua `toErrorInfo`) mã lỗi; **không bao giờ đọc chữ thông điệp** của máy chủ (R6).
 *
 * Thứ tự có chủ ý: các lỗi do chính giao diện sinh (`InvalidApiResponse`, `TimeoutError`) xét trước vì không có trạng thái HTTP;
 * trạng thái 2xx (Angular báo `HttpErrorResponse` khi không đọc được thân) là "chưa rõ kết quả" chứ không phải lỗi thật;
 * rồi mới tới bảng theo trạng thái.
 */
export function classifyFailure(error: unknown): FailureClass {
  if (error instanceof InvalidApiResponse || error instanceof TimeoutError) return 'unknown-outcome';
  if (!(error instanceof HttpErrorResponse)) return 'unexpected';

  const { status } = error;
  if (status >= 200 && status < 300) return 'unknown-outcome';
  if (status >= 300 && status < 400) return 'unexpected';

  const errorInfo = toErrorInfo(error);
  switch (errorInfo.kind) {
    case 'unauthenticated':
      return 'handled';
    case 'network':
    case 'rate-limited':
    case 'csrf':
      return 'transient';
    case 'unavailable':
      // 504: cổng hết giờ chờ nhưng dịch vụ phía sau có thể vẫn xử lý xong; 502, 503: yêu cầu không tới nơi.
      return status === 504 ? 'unknown-outcome' : 'transient';
    case 'forbidden':
      return 'rejected';
    case 'other':
      if (status >= 500) return 'transient';
      return status >= 400 ? 'rejected' : 'unexpected';
  }
}

/**
 * Hạn chờ mặc định của một lời gọi, **cố ý dài hơn** `response-timeout` của BFF (25 giây, `seller-web-bff/application.properties`) để 504 do BFF sinh
 * ra tới trước và mang khung lỗi rõ ràng; hết giờ phía giao diện chỉ là lưới cuối khi cả BFF cũng không trả lời. Thiếu cả hai thì lời gọi treo mãi
 * và nút gửi bị khóa vĩnh viễn.
 */
export const DEFAULT_REQUEST_TIMEOUT_MS = 60_000;

/**
 * Quá hạn mà chưa có giá trị thì phát `TimeoutError` (xếp vào `unknown-outcome` bởi {@link classifyFailure}). Lưu ý hết giờ phía giao diện
 * **không hủy** việc máy chủ đang làm: với thao tác ghi, coi là chưa rõ kết quả và kiểm lại trạng thái, đừng cho gửi lại ngay.
 * Lỗi của nguồn đi qua nguyên vẹn. Dùng được trong `pipe(...)` cho cả đọc và ghi.
 */
export function requestTimeout<T>(ms: number = DEFAULT_REQUEST_TIMEOUT_MS): MonoTypeOperatorFunction<T> {
  return timeout(ms);
}
