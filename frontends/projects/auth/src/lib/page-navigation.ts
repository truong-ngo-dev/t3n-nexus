import { InjectionToken } from '@angular/core';

/**
 * Điều hướng cấp cao (rời SPA, trình duyệt tải trang mới). Đăng nhập là luồng chuyển hướng 302 của
 * Spring, không phải lời gọi `fetch`, nên không dùng Router (ADR-0006, flow.md mục 3).
 * Tách thành token để test thay được, vì jsdom không hỗ trợ điều hướng thật.
 */
export interface PageNavigation {
  assign(url: string): void;
}

export const PAGE_NAVIGATION = new InjectionToken<PageNavigation>('PAGE_NAVIGATION', {
  providedIn: 'root',
  factory: () => ({ assign: (url: string) => window.location.assign(url) }),
});

/** Đường dẫn tương đối cùng nguồn gốc, đi qua cổng tới BFF. */
export const SESSION_URL = '/bff/auth/session';
export const LOGIN_URL = '/bff/auth/login';
export const LOGOUT_URL = '/bff/auth/logout';
