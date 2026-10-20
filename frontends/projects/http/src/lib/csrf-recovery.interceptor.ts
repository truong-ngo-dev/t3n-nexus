import { HttpErrorResponse, HttpInterceptorFn, HttpXsrfTokenExtractor } from '@angular/common/http';
import { inject } from '@angular/core';
import { SessionService } from '@t3n/auth';
import { catchError, switchMap, throwError } from 'rxjs';
import { toApiError } from './api-error';

const SAFE_METHODS = ['GET', 'HEAD'];
const XSRF_HEADER = 'X-XSRF-TOKEN';

/**
 * 403 `CSRF_TOKEN_INVALID` ở yêu cầu ghi: thăm dò phiên (BFF cấp lại cookie `XSRF-TOKEN`) rồi gửi lại
 * đúng một lần với mã đọc lại từ cookie. "Một lần" do cấu trúc: lỗi của bản gửi lại nằm trong `switchMap`, ngoài tầm `catchError`
 * nên không bị bắt lần hai (không cần cờ đánh dấu). Bộ XSRF có sẵn của Angular chạy TRƯỚC bộ chặn này nên bản clone
 * mang mã cũ; phải gắn lại mã mới (đã kiểm bằng test). 403 `FORBIDDEN` và mọi lỗi khác không thử lại.
 *
 * Chỉ bắt lỗi, không `switchMap` trên luồng sự kiện chính: `HttpClient` phát cả `Sent` lẫn `Response`,
 * chạy `switchMap` trên đó sẽ gửi lại hai lần (R4). `probe()` phát đúng một giá trị.
 */
export const csrfRecoveryInterceptor: HttpInterceptorFn = (req, next) => {
  const session = inject(SessionService);
  const xsrf = inject(HttpXsrfTokenExtractor);

  return next(req).pipe(
    catchError((error: unknown) => {
      const recoverable =
        error instanceof HttpErrorResponse &&
        toApiError(error).kind === 'csrf' &&
        !SAFE_METHODS.includes(req.method);
      if (!recoverable) return throwError(() => error);

      return session.probe().pipe(
        switchMap(() => {
          const token = xsrf.getToken();
          const headers = token ? req.headers.set(XSRF_HEADER, token) : req.headers;
          return next(req.clone({ headers }));
        }),
      );
    }),
  );
};
