import { InjectionToken } from '@angular/core';

export interface ApiConfig {
  auth:     string;  // /auth — api-gateway bypass thẳng oauth2-service (register), không qua web-gateway
  webgw:    string;  // /api/webgw
  oauth2:   string;  // /api/oauth2
  identity: string;  // /api/identity
  catalog:  string;  // /api/catalog
  cart:     string;  // /api/cart
  order:    string;  // /api/order
  search:   string;  // /api/search
  chat:     string;  // /api/chat
  seller:   string;  // /api/seller
  customer: string;  // /api/customer
}

export const API_CONFIG = new InjectionToken<ApiConfig>('API_CONFIG');
