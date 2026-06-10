import { InjectionToken } from '@angular/core';

export interface ApiConfig {
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
