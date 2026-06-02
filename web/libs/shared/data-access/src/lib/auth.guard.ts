import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { map, tap } from 'rxjs';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.user() !== null) return true;
  return auth.init().pipe(
    map(() => auth.user() !== null),
    tap(ok => { if (!ok) auth.login(); })
  );
};
