import { HttpErrorResponse, HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { LOGIN_URL, PAGE_NAVIGATION, SESSION_URL } from '@t3n/auth';
import { catchError, tap, throwError } from 'rxjs';

/** Khóa `sessionStorage` của cờ "vừa đi đăng nhập". Chỉ chứa cờ, không chứa token (R11, KT-17). */
export const JUST_SIGNED_IN_FLAG = 'justSignedIn';

/** Trang này đã bắt đầu đi đăng nhập: các 401 song song còn lại bị bỏ qua (không thì chúng đảo cờ, R2). */
@Injectable({ providedIn: 'root' })
class LoginRedirect {
  started = false;
}

function flagSet(): boolean {
  try {
    return sessionStorage.getItem(JUST_SIGNED_IN_FLAG) !== null;
  } catch {
    return false;
  }
}

function setFlag(): void {
  try {
    sessionStorage.setItem(JUST_SIGNED_IN_FLAG, '1');
  } catch {
    // Không ghi được (trình duyệt chặn): chấp nhận mất chống vòng lặp thay vì làm hỏng lời gọi
  }
}

function clearFlag(): void {
  try {
    sessionStorage.removeItem(JUST_SIGNED_IN_FLAG);
  } catch {
    // như trên
  }
}

/**
 * 401 từ API (R2, R9). Chưa có cờ: đặt cờ rồi điều hướng cấp cao tới đăng nhập, một lần. Đã có cờ (vừa đăng
 * nhập mà vẫn 401): xóa cờ và dừng, để giao diện hiện lỗi thay vì vòng lặp qua SSO im lặng. Lỗi luôn được
 * trả tiếp cho người gọi.
 *
 * Thăm dò phiên là ngoại lệ ở cả hai chiều: 401 của nó là trạng thái khách bình thường, và thành công của
 * nó không xóa cờ (không thì bước khởi động sau đăng nhập xóa cờ, 401 kế tiếp lại đi đăng nhập: vòng lặp).
 */
export const unauthorizedInterceptor: HttpInterceptorFn = (req, next) => {
  const navigation = inject(PAGE_NAVIGATION);
  const redirect = inject(LoginRedirect);
  const isSessionProbe = req.url === SESSION_URL;

  return next(req).pipe(
    tap((event) => {
      if (event instanceof HttpResponse && !isSessionProbe) clearFlag();
    }),
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && !isSessionProbe && !redirect.started) {
        if (flagSet()) {
          clearFlag();
        } else {
          setFlag();
          redirect.started = true;
          navigation.assign(LOGIN_URL);
        }
      }
      return throwError(() => error);
    }),
  );
};
