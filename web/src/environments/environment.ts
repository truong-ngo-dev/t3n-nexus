import { ApiConfig } from '@t3n/shared/data-access';

// Base URL của api-gateway — entry point duy nhất cho mọi call BE, kể cả page render bởi
// oauth2-service (adr/009-mobile-gateway.md). Đổi host/port chỉ sửa 1 chỗ này.
const API_BASE = 'http://localhost:8000';

// Toàn bộ endpoint đều absolute, dựng từ API_BASE — cố ý đồng nhất, không trộn absolute/relative
// (cross-origin browser thật sự, dựa vào CORS + withCredentials, xem auth.interceptor.ts — KHÔNG
// dựa vào ng serve proxy). Prefix /web bị api-gateway strip trước khi forward xuống web-gateway.
// Riêng `auth` (register) KHÔNG có /web — api-gateway route /auth/** thẳng đến oauth2-service,
// bypass web-gateway hoàn toàn (ADR-009 + RouteConfiguration của api-gateway).
export const environment = {
  production: false,
  api: {
    auth:     `${API_BASE}/auth`,
    webgw:    `${API_BASE}/web/webgw/auth`,
    oauth2:   `${API_BASE}/web/api/oauth2`,
    identity: `${API_BASE}/web/api/identity`,
    catalog:  `${API_BASE}/web/api/catalog`,
    cart:     `${API_BASE}/web/api/cart`,
    order:    `${API_BASE}/web/api/order`,
    search:   `${API_BASE}/web/api/search`,
    chat:     `${API_BASE}/web/api/chat`,
    seller:   `${API_BASE}/web/api/seller`,
    customer: `${API_BASE}/web/api/customer`,
  } satisfies ApiConfig
};
