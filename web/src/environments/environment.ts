import { ApiConfig } from '@t3n/shared/data-access';

export const environment = {
  production: false,
  api: {
    webgw:    'http://localhost:8090/webgw/auth',
    oauth2:   'http://localhost:8090/api/oauth2',
    identity: 'http://localhost:8090/api/identity',
    catalog:  '/api/catalog',
    cart:     '/api/cart',
    order:    '/api/order',
    search:   '/api/search',
    chat:     '/api/chat',
    seller:   '/api/seller',
    customer: '/api/customer',
  } satisfies ApiConfig
};
