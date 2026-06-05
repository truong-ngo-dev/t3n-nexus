import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const credentialReq = req.clone({ withCredentials: true });
  return next(credentialReq).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401) inject(AuthService).login();
      return throwError(() => err);
    })
  );
};
