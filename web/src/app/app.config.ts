import { ApplicationConfig, inject, provideAppInitializer } from '@angular/core';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { API_CONFIG, AuthService, authInterceptor } from '@t3n/shared/data-access';
import { environment } from '../environments/environment';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideAnimationsAsync(),
    provideHttpClient(withInterceptors([authInterceptor])),

    { provide: API_CONFIG, useValue: environment.api },

    provideAppInitializer(() => inject(AuthService).init()),
  ]
};
