import {ApplicationConfig, provideZoneChangeDetection, provideAppInitializer, inject} from '@angular/core';
import {provideRouter} from '@angular/router';

import {routes} from './app.routes';
import {provideHttpClient, withInterceptors, withXhr} from '@angular/common/http'
import {csrfRecoveryInterceptor, unauthorizedInterceptor} from '@t3n/http';
import {SellerBootstrapService} from './shared/service/seller-bootstrap.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([unauthorizedInterceptor, csrfRecoveryInterceptor]), withXhr()),
    provideAppInitializer(() => {
      const bootstrapService = inject(SellerBootstrapService);
      return bootstrapService.load();
    })
  ]
};
