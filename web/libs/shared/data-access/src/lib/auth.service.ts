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
    // return this.http.get<User>(`${this.config.identity}/me`).pipe(
    //   tap(u  => { this.user.set(u); this.loading.set(false); }),
    //   catchError(() => { this.user.set(null); this.loading.set(false); return of(null); }),
    //   map(() => void 0)
    // );
    return undefined;
  }

  // BFF redirect thẳng 302 → oauth2-service login form
  login(): void {
    window.location.href = `${this.config.bff}/login`;
  }

  // BFF invalidates session, returns 202 + Location header → SPA navigates to OIDC end_session
  logout(): void {
    this.http.post(`${this.config.bff}/logout`, {}, { observe: 'response' })
      .subscribe(res => {
        const location = res.headers.get('Location');
        if (location) window.location.href = location;
      });
  }
}
