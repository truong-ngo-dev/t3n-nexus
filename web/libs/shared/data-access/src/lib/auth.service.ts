import { inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, map, Observable, of, tap } from 'rxjs';
import { API_CONFIG } from './api-config';
import { Role, User } from '@t3n/shared/model';

interface SessionResponse {
  userId: string;
  role:   Role;
}

export interface RegisterRequest {
  email:    string;
  password: string;
  fullName: string;
}

interface RegisterResponse {
  userId: string;
}

interface ApiResponse<T> {
  data: T;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http   = inject(HttpClient);
  private readonly config = inject(API_CONFIG);

  readonly user    = signal<User | null>(null);
  readonly loading = signal(true);

  init(): Observable<void> {
    return this.http.get<SessionResponse>(`${this.config.webgw}/session`).pipe(
      tap(res => {
        this.user.set({ id: res.userId, role: res.role, fullName: '', avatarUrl: null });
        this.loading.set(false);
      }),
      catchError(() => {
        this.user.set(null);
        this.loading.set(false);
        return of(null);
      }),
      map(() => void 0)
    );
  }

  patchUser(partial: Partial<User>): void {
    const current = this.user();
    if (current) this.user.set({ ...current, ...partial });
  }

  // BFF redirect thẳng 302 → oauth2-service login form
  login(): void {
    window.location.href = `${this.config.webgw}/login`;
  }

  // Bypass web-gateway — gọi thẳng api-gateway /auth/register → oauth2-service (ADR-009).
  // role luôn CUSTOMER: đây là form đăng ký tự-phục-vụ cho buyer, seller/admin không self-register.
  register(req: RegisterRequest): Observable<RegisterResponse> {
    return this.http.post<ApiResponse<RegisterResponse>>(`${this.config.auth}/register`, {
      ...req,
      role: 'CUSTOMER',
    }).pipe(map(res => res.data));
  }

  // BFF invalidates session, returns 202 + Location header → SPA navigates to OIDC end_session
  logout(): void {
    this.http.post(`${this.config.webgw}/logout`, {}, { observe: 'response' })
      .subscribe(res => {
        const location = res.headers.get('Location');
        if (location) window.location.href = location;
      });
  }
}
