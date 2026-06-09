import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const storefrontGuard: CanActivateFn = () => {
  const auth   = inject(AuthService);
  const router = inject(Router);
  const user   = auth.user();
  if (!user || user.role === 'CUSTOMER') return true;
  if (user.role === 'SELLER') return router.createUrlTree(['/seller']);
  if (user.role === 'ADMIN')  return router.createUrlTree(['/admin']);
  return router.createUrlTree(['/login']);
};
