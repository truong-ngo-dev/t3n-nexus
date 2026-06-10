import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import { API_CONFIG } from './api-config';
import { DeviceItem, LoginHistoryItem, PagedData } from '@t3n/shared/model';

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

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword:     string;
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

  getLoginHistory(page = 0, size = 5): Observable<PagedData<LoginHistoryItem>> {
    return this.http.get<ApiResponse<PagedData<LoginHistoryItem>>>(
      `${this.config.identity}/v1/me/login-history`, { params: { page, size } }
    ).pipe(map(res => res.data));
  }

  getDevices(): Observable<DeviceItem[]> {
    return this.http.get<ApiResponse<DeviceItem[]>>(
      `${this.config.identity}/v1/me/devices`
    ).pipe(map(res => res.data));
  }

  revokeDevice(deviceId: string): Observable<void> {
    return this.http.delete<void>(`${this.config.identity}/v1/me/devices/${deviceId}`);
  }

  getPasswordStatus(): Observable<{ hasPassword: boolean }> {
    return this.http.get<ApiResponse<{ hasPassword: boolean }>>(
      `${this.config.oauth2}/v1/me/password/status`
    ).pipe(map(res => res.data));
  }

  changePassword(req: ChangePasswordRequest): Observable<void> {
    return this.http.put<void>(`${this.config.oauth2}/v1/me/password`, req);
  }

  requestPasswordSetup(): Observable<void> {
    return this.http.post<void>(`${this.config.oauth2}/v1/me/password/setup-request`, {});
  }
}
