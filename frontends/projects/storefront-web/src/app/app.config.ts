import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, inject, provideAppInitializer, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { AUTH_BASE_URL, SessionService } from '@t3n/auth';
import { csrfRecoveryInterceptor, unauthorizedInterceptor } from '@t3n/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../environments/environment';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    // Thứ tự: đầu danh sách là lớp ngoài. Phục hồi CSRF ở trong, nên 401 chỉ thấy kết quả sau khi đã phục hồi.
    provideHttpClient(withInterceptors([unauthorizedInterceptor, csrfRecoveryInterceptor])),
    // Chờ thăm dò phiên xong (kể cả lỗi: probe không bao giờ lỗi) trước khi định tuyến đầu tiên, để guard đọc được trạng thái có nghĩa (T3)
    provideAppInitializer(() => firstValueFrom(inject(SessionService).probe())),
    { provide: AUTH_BASE_URL, useValue: environment.authBaseUrl },
    provideRouter(routes),
  ],
};
