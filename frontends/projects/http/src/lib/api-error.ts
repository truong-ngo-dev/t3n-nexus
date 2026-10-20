import { HttpErrorResponse } from '@angular/common/http';

export type ApiErrorKind = 'rate-limited' | 'unavailable' | 'network' | 'unauthenticated' | 'csrf' | 'forbidden' | 'other';

/** Lỗi đã chuẩn hóa để giao diện chọn thông báo. Không chứa chữ thông điệp của máy chủ (R6: không đoán lý do từ chữ). */
export interface ApiError {
  kind: ApiErrorKind;
  status?: number;
  /** Mã lỗi của khung lỗi chung (`EMAIL_TAKEN`, ...), nếu phản hồi có. */
  code?: string;
  /** Chỉ có ở `rate-limited` và chỉ khi `Retry-After` là số giây hợp lệ. */
  retryAfterSeconds?: number;
}

function codeOf(body: unknown): string | undefined {
  if (typeof body !== 'object' || body === null) return undefined;
  const { code } = body as Record<string, unknown>;
  return typeof code === 'string' ? code : undefined;
}

function secondsOf(header: string | null): number | undefined {
  if (header === null || !/^\d+$/.test(header.trim())) return undefined;
  return Number(header.trim());
}

/**
 * Chuẩn hóa lỗi HTTP. Hàm thuần, KHÔNG phải bộ chặn: biến đổi kiểu lỗi trong chuỗi bộ chặn sẽ làm
 * `SessionService.probe()` không còn nhận ra 401. Không đổi trạng thái phiên (KT-06).
 */
export function toApiError(error: unknown): ApiError {
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
  if (status === 503) return { kind: 'unavailable', status };
  return { kind: 'other', status, ...(code && { code }) };
}
