/**
 * Dịch một lỗi HTTP thô (`HttpErrorResponse`) thành {@link ErrorInfo}: dữ liệu mô tả đã chuẩn hóa để bộ chặn và tính năng đọc.
 *
 * ## Vai trò trong chuỗi xử lý lỗi
 * ```
 * HttpErrorResponse ──▶ toErrorInfo ──▶ ErrorInfo ──▶ classifyFailure (api-response.ts) ──▶ FailureClass
 *   (thô, thân unknown)   (file này)    (kind, status, code, errors)   (quyết định làm gì tiếp)
 * ```
 * File này chỉ **đọc và chuẩn hóa**, không quyết định hành xử (thử lại, kiểm lại trạng thái, hiển thị gì): việc đó của `classifyFailure`
 * và của từng tính năng. Phản hồi thành công (2xx) và lỗi do giao diện tự sinh (`InvalidApiResponse`, `TimeoutError`) không đi qua đây.
 *
 * ## Nguyên tắc
 * - **Quyết định theo mã trạng thái trước.** Thân lỗi không đáng tin (có thể rỗng, HTML, hoặc do tầng khác sinh), nên `kind` chỉ phụ
 *   thuộc vào `status`; riêng 403 xét thêm `code` để tách lỗi CSRF khỏi bị từ chối thật.
 * - **Chỉ đọc `code` và `errors` khi đúng kiểu** (chuỗi, mảng chuỗi); sai kiểu thì coi như vắng, không ném.
 * - **Không đọc chữ thông điệp (`message`) của máy chủ** (R6): chữ có thể đổi và lộ chi tiết nội bộ, câu hiển thị do giao diện tự đặt.
 *
 * ## Bảng dịch
 * | Điều kiện | `kind` | Trường kèm theo |
 * |---|---|---|
 * | Không phải `HttpErrorResponse` | `other` | (không có) |
 * | `status` 0 (mất mạng) | `network` | `status` |
 * | 401 | `unauthenticated` | `status`, `code` |
 * | 403 với `code = CSRF_TOKEN_INVALID` | `csrf` | `status`, `code` |
 * | 403 khác | `forbidden` | `status`, `code` |
 * | 429 | `rate-limited` | `status`, `retryAfterSeconds` (nếu `Retry-After` hợp lệ) |
 * | 502, 503, 504 | `unavailable` | `status` |
 * | Còn lại (400, 404, 409, 500...) | `other` | `status`, `code`, `errors` |
 *
 * `errors` chỉ được đọc ở `other`, vì chỉ lỗi nghiệp vụ của `seller-service` mang tên trường sai (`errors[0]`).
 *
 * ## Ví dụ
 * ```ts
 * toErrorInfo(new HttpErrorResponse({ status: 409, error: { code: 'SHOP_NAME_TAKEN', message: 'trùng' } }));
 * // { kind: 'other', status: 409, code: 'SHOP_NAME_TAKEN' }          (không có message)
 *
 * toErrorInfo(new HttpErrorResponse({ status: 400, error: { code: 'SELLER_FIELD_INVALID', errors: ['shopName'] } }));
 * // { kind: 'other', status: 400, code: 'SELLER_FIELD_INVALID', errors: ['shopName'] }
 *
 * toErrorInfo(new HttpErrorResponse({ status: 429, headers: new HttpHeaders({ 'Retry-After': '30' }) }));
 * // { kind: 'rate-limited', status: 429, retryAfterSeconds: 30 }
 *
 * toErrorInfo(new HttpErrorResponse({ status: 504 }));
 * // { kind: 'unavailable', status: 504 }   (giữ status: nơi gọi cần để phân biệt 504 với 502, 503)
 * ```
 *
 * @module
 */
import { HttpErrorResponse } from '@angular/common/http';

/**
 * Bản chất của lỗi theo mã trạng thái; xem bảng dịch ở đầu file.
 * - `rate-limited`: 429. `unavailable`: 502, 503, 504. `network`: mất mạng (status 0).
 * - `unauthenticated`: 401. `csrf`: 403 do mã CSRF sai. `forbidden`: 403 khác.
 * - `other`: mọi lỗi còn lại, và cả giá trị không phải `HttpErrorResponse`.
 */
export type ErrorInfoKind = 'rate-limited' | 'unavailable' | 'network' | 'unauthenticated' | 'csrf' | 'forbidden' | 'other';

/**
 * Lỗi đã chuẩn hóa để giao diện chọn thông báo. Không chứa chữ thông điệp của máy chủ (R6: không đoán lý do từ chữ).
 *
 * Đây là **dữ liệu mô tả**, không phải lớp `Error` để ném (khác {@link InvalidApiResponse} ở `api-response.ts`).
 */
export interface ErrorInfo {
  /** Loại lỗi, quyết định theo mã trạng thái. */
  kind: ErrorInfoKind;
  /** Mã trạng thái HTTP; vắng khi giá trị đầu vào không phải `HttpErrorResponse`. */
  status?: number;
  /** Mã lỗi của khung lỗi chung (`EMAIL_TAKEN`, ...), nếu phản hồi có. Chỉ được điền ở `unauthenticated`, `csrf`, `forbidden`, `other`. */
  code?: string;
  /** Chi tiết của khung lỗi chung (ví dụ tên trường sai), chỉ phần tử chuỗi; vắng khi không có phần tử nào dùng được. Chỉ điền ở `other`. */
  errors?: string[];
  /** Chỉ có ở `rate-limited` và chỉ khi `Retry-After` là số giây hợp lệ. */
  retryAfterSeconds?: number;
}

/**
 * Đọc `code` từ thân lỗi.
 *
 * @param body thân lỗi thô, có thể là bất cứ thứ gì (đối tượng, chuỗi, `null`...)
 * @returns `code` nếu thân là đối tượng có `code` kiểu chuỗi; ngược lại `undefined`
 */
function codeOf(body: unknown): string | undefined {
  if (typeof body !== 'object' || body === null) return undefined;
  const { code } = body as Record<string, unknown>;
  return typeof code === 'string' ? code : undefined;
}

/**
 * Đọc `errors` từ thân lỗi, bỏ mọi phần tử không phải chuỗi.
 *
 * @param body thân lỗi thô
 * @returns mảng các chuỗi trong `errors`; `undefined` nếu `errors` không phải mảng hoặc không còn phần tử chuỗi nào
 */
function errorsOf(body: unknown): string[] | undefined {
  if (typeof body !== 'object' || body === null) return undefined;
  const { errors } = body as Record<string, unknown>;
  if (!Array.isArray(errors)) return undefined;
  const strings = errors.filter((entry): entry is string => typeof entry === 'string');
  return strings.length > 0 ? strings : undefined;
}

/**
 * Đọc giá trị `Retry-After` dạng số giây.
 *
 * @param header giá trị header, `null` nếu không có
 * @returns số giây; `undefined` nếu vắng hoặc không phải số nguyên không âm (dạng ngày giờ của HTTP không được hỗ trợ)
 */
function secondsOf(header: string | null): number | undefined {
  if (header === null || !/^\d+$/.test(header.trim())) return undefined;
  return Number(header.trim());
}

/**
 * Chuẩn hóa lỗi HTTP. Hàm thuần, KHÔNG phải bộ chặn: biến đổi kiểu lỗi trong chuỗi bộ chặn sẽ làm
 * `SessionService.probe()` không còn nhận ra 401. Không đổi trạng thái phiên (KT-06).
 *
 * Không ném với đầu vào lạ: giá trị không phải `HttpErrorResponse` cho `{ kind: 'other' }`.
 *
 * @param error lỗi nhận được từ kênh lỗi của `HttpClient`, còn `unknown`
 * @returns {@link ErrorInfo} theo bảng dịch ở đầu file
 */
export function toErrorInfo(error: unknown): ErrorInfo {
  if (!(error instanceof HttpErrorResponse)) return { kind: 'other' };

  const { status } = error;
  const code = codeOf(error.error);

  if (status === 0) return { kind: 'network', status };
  if (status === 401) return { kind: 'unauthenticated', status, ...(code && { code }) };
  if (status === 403) return { kind: code === 'CSRF_TOKEN_INVALID' ? 'csrf' : 'forbidden', status, ...(code && { code }) };
  if (status === 429) {
    const retryAfterSeconds = secondsOf(error.headers.get('Retry-After'));
    return { kind: 'rate-limited', status, ...(retryAfterSeconds !== undefined && { retryAfterSeconds }) };
  }
  // 502/503/504: hệ thống tạm thời không với tới được (cổng hoặc dịch vụ phía sau), người dùng thử lại sau.
  // Giữ `status`: với thao tác ghi, 504 có thể đã được xử lý nên nơi gọi phải kiểm lại trạng thái trước khi gửi lại.
  if (status === 502 || status === 503 || status === 504) return { kind: 'unavailable', status };
  const errors = errorsOf(error.error);
  return { kind: 'other', status, ...(code && { code }), ...(errors && { errors }) };
}
