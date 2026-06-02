import { ApplicationConfig, provideAppInitializer, inject } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { API_CONFIG, AuthService, authInterceptor } from '@t3n/shared/data-access';
import { environment } from '../environments/environment';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),

    { provide: API_CONFIG, useValue: environment.api },

    provideAppInitializer(() => inject(AuthService).init())
  ]
};
