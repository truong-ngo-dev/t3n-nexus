import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { Role } from '@t3n/shared/model';
import { AuthService } from './auth.service';

export function roleGuard(requiredRole: Role): CanActivateFn {
  return () => {
    const auth   = inject(AuthService);
    const router = inject(Router);

    const check = () => {
      const user = auth.user();
      if (!user) { auth.login(); return false; }
      if (user.role !== requiredRole) {
        return router.createUrlTree([`/${user.role.toLowerCase()}`]);
      }
      return true;
    };

    if (auth.user() !== null) return check();
    return auth.init().pipe(map(() => check()));
  };
}
