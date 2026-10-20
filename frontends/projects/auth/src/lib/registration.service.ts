import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, InjectionToken, inject } from '@angular/core';
import { Observable, catchError, map, of } from 'rxjs';

/** Địa chỉ gốc của Xác thực (host `auth` qua cổng, khác nguồn gốc với ứng dụng). Ứng dụng cấp, không cứng trong thư viện. */
export const AUTH_BASE_URL = new InjectionToken<string>('AUTH_BASE_URL');

export type RegisterFailure =
  | { kind: 'email-taken' }
  | { kind: 'email-unusable' }
  | { kind: 'password-invalid' }
  | { kind: 'invalid' }
  | { kind: 'rate-limited'; retryAfterSeconds?: number }
  | { kind: 'other' };

export type RegisterResult = { kind: 'created' } | { kind: 'failed'; failure: RegisterFailure };

function codeOf(body: unknown): string | undefined {
  if (typeof body !== 'object' || body === null) return undefined;
  const { code } = body as Record<string, unknown>;
  return typeof code === 'string' ? code : undefined;
}

function secondsOf(header: string | null): number | undefined {
  const value = header?.trim();
  return value && /^\d+$/.test(value) ? Number(value) : undefined;
}

/** Chỉ rẽ theo mã lỗi của khung lỗi chung, không đoán lý do từ chữ của thông điệp (R6). */
function toFailure(error: unknown): RegisterFailure {
  if (!(error instanceof HttpErrorResponse)) return { kind: 'other' };
  const code = codeOf(error.error);
  if (error.status === 429) {
    const retryAfterSeconds = secondsOf(error.headers.get('Retry-After'));
    return retryAfterSeconds === undefined ? { kind: 'rate-limited' } : { kind: 'rate-limited', retryAfterSeconds };
  }
  if (error.status === 409 && code === 'EMAIL_TAKEN') return { kind: 'email-taken' };
  if (error.status === 400) {
    switch (code) {
      case 'EMAIL_INVALID':
      case 'EMAIL_RESERVED':
        return { kind: 'email-unusable' };
      case 'PASSWORD_INVALID':
        return { kind: 'password-invalid' };
      case 'VALIDATION_FAILED':
        return { kind: 'invalid' };
    }
  }
  return { kind: 'other' };
}

/**
 * Đăng ký tài khoản (H1): `POST {auth}/register` chỉ với `{email, password}`. Lời gọi khác nguồn gốc, không cookie,
 * Angular không gắn `X-XSRF-TOKEN` (miễn CSRF phía Xác thực). Luồng luôn hoàn tất bình thường, lỗi nằm trong kết quả.
 */
@Injectable({ providedIn: 'root' })
export class RegistrationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(AUTH_BASE_URL);

  register(email: string, password: string): Observable<RegisterResult> {
    return this.http.post(`${this.baseUrl}/register`, { email, password }, { withCredentials: false }).pipe(
      map((): RegisterResult => ({ kind: 'created' })),
      catchError((error: unknown) => of<RegisterResult>({ kind: 'failed', failure: toFailure(error) })),
    );
  }
}
