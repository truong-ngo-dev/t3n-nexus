/**
 * Khung phản hồi của backend (xem `docs/global/4.convention/api-response.md`). Kết quả của yêu cầu do mã trạng thái HTTP quyết định; FE không đọc `success` hay `code` trong thân để biết thành công.
 *
 * Các trường tùy chọn chỉ có khi backend gửi: `message` và `timestamp` chỉ ở lỗi, `error` chỉ ở lỗi cần chi tiết (chuỗi, đối tượng hoặc mảng).
 */
export interface ApiResponse<T> {
  /** `OK` khi thành công, ngược lại là mã lỗi ổn định. */
  code?: string;
  data: T;
  message: string | null;
  traceId: string;
  /** Chi tiết lỗi; đọc theo kiểu, kiểu lạ coi như vắng. */
  error?: unknown;
  /** ISO-8601 UTC, chỉ ở lỗi. */
  timestamp?: string;
  /** Dạng cũ, còn được gửi trong giai đoạn chuyển tiếp; không dùng để quyết định. */
  errors?: string[] | null;
  /** Dạng cũ, còn được gửi trong giai đoạn chuyển tiếp; không dùng để quyết định. */
  success?: boolean;
}
