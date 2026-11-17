import {CanActivateFn, Router} from '@angular/router';
import {inject} from '@angular/core';
import {LOGIN_URL, PAGE_NAVIGATION, SessionService} from '@t3n/auth';

export const FORBIDDEN_ACCOUNT_URL = '/forbidden-account';

export const sellerGuard: CanActivateFn = () => {
  const state = inject(SessionService).state();
  switch (state.kind) {
    case "authenticated":
      const role = state.session.role;
      if (role === 'USER') return true;
      if (role === 'ADMIN') return  inject(Router).createUrlTree([FORBIDDEN_ACCOUNT_URL]);
      return false;
    case "anonymous":
      inject(PAGE_NAVIGATION).assign(LOGIN_URL);
      return false;
    default:
      return false;
  }
};

export const loginPageGuard: CanActivateFn = () => {
  const state = inject(SessionService).state();
  switch (state.kind) {
    case "authenticated": return inject(Router).createUrlTree(["/"]);
    default: return true;
  }
};
