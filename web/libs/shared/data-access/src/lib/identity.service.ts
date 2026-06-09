import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import { API_CONFIG } from './api-config';

export interface UserProfile {
  userId:      string;
  fullName:    string;
  email:       string;
  phoneNumber: string | null;
  avatarUrl:   string | null;
}

export interface UpdateProfileRequest {
  fullName:    string;
  phoneNumber: string | null;
}

interface ApiResponse<T> {
  data: T;
}

@Injectable({ providedIn: 'root' })
export class IdentityService {
  private readonly http   = inject(HttpClient);
  private readonly config = inject(API_CONFIG);

  getMe(): Observable<UserProfile> {
    return this.http.get<ApiResponse<UserProfile>>(`${this.config.identity}/v1/me`).pipe(
      map(res => res.data)
    );
  }

  updateProfile(req: UpdateProfileRequest): Observable<UserProfile> {
    return this.http.put<ApiResponse<UserProfile>>(`${this.config.identity}/v1/me`, req).pipe(
      map(res => res.data)
    );
  }

  uploadAvatar(file: File): Observable<{ avatarUrl: string }> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ApiResponse<{ avatarUrl: string }>>(`${this.config.identity}/v1/me/avatar`, form).pipe(
      map(res => res.data)
    );
  }
}
