import { ApiConfig } from '@t3n/shared/data-access';

// Nguồn gốc theo subdomain (ADR-0006, feature 06 nhóm E): mở ứng dụng ở http://shop.localhost:8000 (api-gateway chuyển mọi đường dẫn
// không thuộc BFF tới máy chủ giao diện ng serve), KHÔNG mở ở http://localhost:4200 (host không có trong danh sách của cổng, nhận 404).
// Giao diện và BFF cùng nguồn gốc nên BFF và API gọi bằng đường tương đối (cookie phiên và mã CSRF thuộc đúng một nguồn gốc);
// riêng Xác thực ở host auth, gọi chéo nguồn gốc (đăng ký, không cần cookie).
const AUTH_BASE = 'http://auth.localhost:8000';

export const environment = {
  production: false,
  api: {
    auth:     AUTH_BASE,
    webgw:    '/bff/auth',
    oauth2:   '/api/oauth2',
    identity: '/api/identity',
    catalog:  '/api/catalog',
    cart:     '/api/cart',
    order:    '/api/order',
    search:   '/api/search',
    chat:     '/api/chat',
    seller:   '/api/seller',
    customer: '/api/customer',
  } satisfies ApiConfig
};
