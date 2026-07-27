import { ApiConfig } from '@t3n/shared/data-access';

// Mọi call đều qua api-gateway (entry point duy nhất — adr/009-mobile-gateway.md).
// Prefix /web bị api-gateway strip trước khi forward xuống web-gateway.
export const environment = {
  production: false,
  api: {
    webgw:    'http://localhost:8000/web/webgw/auth',
    oauth2:   'http://localhost:8000/web/api/oauth2',
    identity: 'http://localhost:8000/web/api/identity',
    catalog:  '/web/api/catalog',
    cart:     '/web/api/cart',
    order:    '/web/api/order',
    search:   '/web/api/search',
    chat:     '/web/api/chat',
    seller:   '/web/api/seller',
    customer: '/web/api/customer',
  } satisfies ApiConfig
};
