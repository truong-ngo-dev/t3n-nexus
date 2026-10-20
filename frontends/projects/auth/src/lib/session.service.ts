import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Session, parseSession } from '@t3n/model';
import { Observable, catchError, map, of, tap } from 'rxjs';
import { LOGOUT_URL, PAGE_NAVIGATION, SESSION_URL } from './page-navigation';

/**
 * Trạng thái phiên: ba nghĩa với người dùng (đang kiểm, khách hoặc người dùng, lỗi), tech.md T2.
 * `unknown` là lỗi: KHÔNG được coi là khách, vì sẽ đẩy người đang đăng nhập đi đăng nhập oan (R10).
 */
export type SessionState =
  | { kind: 'loading' }
  | { kind: 'anonymous' }
  | { kind: 'authenticated'; session: Session }
  | { kind: 'unknown' };

/** Kết quả đăng xuất: đã điều hướng đi, hoặc thất bại (phiên vẫn có thể còn hiệu lực ở BFF, R9). */
export type LogoutResult = { kind: 'redirected' } | { kind: 'failed' };

/**
 * `Location` chỉ nhận đường dẫn tương đối cùng nguồn gốc hoặc địa chỉ http(s). Chặn `javascript:`, `data:`
 * và `//host` (tương đối theo giao thức) để phản hồi lạ không biến thành điều hướng tới nơi khác.
 */
function isSafeLocation(location: string): boolean {
  return /^https?:\/\//i.test(location) || (location.startsWith('/') && !location.startsWith('//'));
}

@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly http = inject(HttpClient);
  private readonly navigation = inject(PAGE_NAVIGATION);
  private readonly _state = signal<SessionState>({ kind: 'loading' });

  readonly state = this._state.asReadonly();

  /**
   * Thăm dò phiên: `GET /bff/auth/session`.
   * 200 hợp lệ → người dùng; 401 → khách (trạng thái bình thường, không chuyển hướng);
   * mọi thứ khác (5xx, mạng, 200 sai khuôn hoặc vai lạ) → không xác định.
   * Luồng luôn hoàn tất bình thường, kể cả khi lỗi, để bộ khởi động không bị kẹt.
   */
  probe(): Observable<void> {
    return this.http.get<unknown>(SESSION_URL).pipe(
      map((body): SessionState => {
        const session = parseSession(body);
        return session ? { kind: 'authenticated', session } : { kind: 'unknown' };
      }),
      catchError((error: unknown) =>
        of<SessionState>(error instanceof HttpErrorResponse && error.status === 401 ? { kind: 'anonymous' } : { kind: 'unknown' }),
      ),
      tap((state) => this._state.set(state)),
      map(() => undefined),
    );
  }

  /**
   * Đăng xuất (H5): `POST /bff/auth/logout`, nhận 202 thì điều hướng cấp cao tới `Location` (tới host `auth`
   * để xóa phiên ở đó rồi về trang đích; chỉ điều hướng cấp cao mới mang được cookie của host `auth`).
   * Trang tải lại hoàn toàn nên phiên được thăm dò lại lúc khởi động, không cần đổi trạng thái ở đây.
   * Mọi thất bại (lỗi mạng, 5xx, thiếu hoặc lạ `Location`) KHÔNG đổi trạng thái phiên và không điều hướng.
   * 403 CSRF đã được bộ chặn phục hồi trước khi tới đây. Luồng luôn hoàn tất bình thường.
   */
  logout(): Observable<LogoutResult> {
    return this.http.post(LOGOUT_URL, null, { observe: 'response' }).pipe(
      map((response): LogoutResult => {
        const location = response.headers.get('Location');
        if (response.status !== 202 || !location || !isSafeLocation(location)) return { kind: 'failed' };
        this.navigation.assign(location);
        return { kind: 'redirected' };
      }),
      catchError(() => of<LogoutResult>({ kind: 'failed' })),
    );
  }
}
