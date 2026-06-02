import { inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, map, Observable, of, tap } from 'rxjs';
import { API_CONFIG } from './api-config';
import { User } from '@t3n/shared/model';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http   = inject(HttpClient);
  private readonly config = inject(API_CONFIG);

  readonly user    = signal<User | null>(null);
  readonly loading = signal(true);

  init(): Observable<void> {
    return this.http.get<User>(`${this.config.identity}/me`).pipe(
      tap(u  => { this.user.set(u); this.loading.set(false); }),
      catchError(() => { this.user.set(null); this.loading.set(false); return of(null); }),
      map(() => void 0)
    );
  }

  // BFF redirect thẳng 302 → oauth2-service login form
  login(): void {
    window.location.href = `${this.config.bff}/login`;
  }

  // BFF invalidate session, trả logout URL (có id_token_hint) → navigate đến oauth2-service
  logout(): void {
    this.http.post<{ url: string }>(`${this.config.bff}/logout`, {})
      .subscribe(res => window.location.href = res.url);
  }
}
