import { Routes } from '@angular/router';
import {HomeComponent} from './pages/home/home.component';
import {loginPageGuard, sellerGuard} from './shared/guard/seller.guard';
import {LoginComponent} from './pages/login/login.component';
import {ForbiddenAccountComponent} from './pages/forbidden-account/forbidden-account.component';
import {NotFoundComponent} from './pages/not-found/not-found.component';

export const routes: Routes = [
  {
    path: '', component: HomeComponent, canActivate: [sellerGuard]
  },
  {
    path: 'login', component: LoginComponent, canActivate: [loginPageGuard]
  },
  {
    path: 'forbidden-account', component: ForbiddenAccountComponent,
  },
  {
    path: '**', component: NotFoundComponent,
  }
];
