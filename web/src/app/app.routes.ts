import { Routes } from '@angular/router';
import { authGuard, roleGuard, storefrontGuard } from '@t3n/shared/data-access';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./login/login.component').then(m => m.LoginComponent),
  },
  {
    path: 'customer',
    canActivate: [storefrontGuard],
    loadComponent: () => import('./customer/customer-portal.component').then(m => m.CustomerPortalComponent),
    children: [
      {
        path: 'account',
        canActivate: [authGuard],
        loadComponent: () => import('./customer/account/account-shell.component').then(m => m.AccountShellComponent),
        children: [
          { path: '',        pathMatch: 'full', redirectTo: 'profile' },
          { path: 'profile', loadComponent: () => import('@t3n/storefront/feature-profile').then(m => m.AccountProfileComponent) },
        ],
      },
    ],
  },
  {
    path: 'seller',
    canActivate: [roleGuard('SELLER')],
    loadComponent: () => import('./seller/seller-portal.component').then(m => m.SellerPortalComponent),
  },
  {
    path: 'admin',
    canActivate: [roleGuard('ADMIN')],
    loadComponent: () => import('./admin/admin-portal.component').then(m => m.AdminPortalComponent),
  },
  { path: '',   pathMatch: 'full', redirectTo: 'customer' },
  { path: '**', redirectTo: 'customer' },
];
