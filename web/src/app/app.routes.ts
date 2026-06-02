import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./login/login.component').then(m => m.LoginComponent),
  },
  {
    path: 'customer',
    loadComponent: () => import('./customer/customer-portal.component').then(m => m.CustomerPortalComponent),
  },
  {
    path: 'seller',
    loadComponent: () => import('./seller/seller-portal.component').then(m => m.SellerPortalComponent),
  },
  {
    path: 'admin',
    loadComponent: () => import('./admin/admin-portal.component').then(m => m.AdminPortalComponent),
  },
  {
    path: '',
    redirectTo: 'login',
    pathMatch: 'full',
  },
  {
    path: '**',
    redirectTo: 'login',
  },
];
