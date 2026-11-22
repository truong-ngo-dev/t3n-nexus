import {ApplicationConfig, provideZoneChangeDetection, provideAppInitializer, inject} from '@angular/core';
import {provideRouter, withComponentInputBinding} from '@angular/router';

import {routes} from './app.routes';
import {provideHttpClient, withInterceptors, withXhr} from '@angular/common/http'
import {csrfRecoveryInterceptor, unauthorizedInterceptor} from '@t3n/http';
import {SellerBootstrapService} from './core/service/bootstrap/seller-bootstrap.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([unauthorizedInterceptor, csrfRecoveryInterceptor]), withXhr()),
    provideAppInitializer(() => {
      const bootstrapService = inject(SellerBootstrapService);
      return bootstrapService.load();
    })
  ]
};
