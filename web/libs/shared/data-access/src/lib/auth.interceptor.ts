import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { API_CONFIG } from './api-config';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  // inject() chỉ hợp lệ trong injection context đồng bộ lúc interceptor function chạy — PHẢI lấy
  // ra ở đây. Bản cũ gọi inject(AuthService) bên trong callback của catchError (chạy async, ngoài
  // injection context) → ném NG0203 mỗi lần — .login() chưa bao giờ thực sự chạy được, với bất kỳ
  // 401 nào chứ không riêng gì session-check. Đây mới là nguyên nhân thật khiến không có redirect nào xảy ra.
  const config      = inject(API_CONFIG);
  const authService = inject(AuthService);
  const credentialReq = req.clone({ withCredentials: true });

  // `${webgw}/session` (AuthService.init(), gọi mỗi lần app bootstrap) trả 401 như trạng thái
  // BÌNH THƯỜNG cho khách chưa đăng nhập — không phải lỗi cần redirect. Giờ inject() đã hoạt động
  // thật, phải loại trừ case này tường minh, nếu không mọi khách anonymous sẽ bị bounce ngay lúc
  // vừa load app (kể cả đang đứng ở /login, /register). Các 401 khác (session hết hạn giữa lúc
  // dùng trang cần auth) vẫn cần redirect — đó là hành vi dự định ban đầu, giờ mới thật sự chạy.
  const isSessionCheck = req.url === `${config.webgw}/session`;

  return next(credentialReq).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && !isSessionCheck) authService.login();
      return throwError(() => err);
    })
  );
};
