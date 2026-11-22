import { Routes } from '@angular/router';
import {HomeComponent} from './features/home/home.component';
import {loginPageGuard, sellerGuard} from './core/guard/seller.guard';
import {
  canEditApprovedGuard, canEditPendingGuard, canResubmitProfileGuard, canSubmitProfileGuard, hasProfileGuard,
} from './core/guard/profile-state.guard';
import {LoginComponent} from './core/pages/login/login.component';
import {ForbiddenAccountComponent} from './core/pages/forbidden-account/forbidden-account.component';
import {NotFoundComponent} from './core/pages/not-found/not-found.component';
import {ProfileFormComponent} from './features/seller-profile/pages/profile-form/profile-form.component';
import {ProfileViewComponent} from './features/seller-profile/pages/profile-view/profile-view.component';

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
    path: 'profile', component: ProfileViewComponent, canActivate: [sellerGuard, hasProfileGuard],
  },
  {
    // `mode` vào `input()` của form nhờ `withComponentInputBinding()` (app.config.ts).
    path: 'profile/new', component: ProfileFormComponent, canActivate: [sellerGuard, canSubmitProfileGuard], data: {mode: 'submit'},
  },
  {
    path: 'profile/edit', component: ProfileFormComponent, canActivate: [sellerGuard, canEditPendingGuard], data: {mode: 'edit-pending'},
  },
  {
    path: 'profile/resubmit', component: ProfileFormComponent, canActivate: [sellerGuard, canResubmitProfileGuard], data: {mode: 'resubmit'},
  },
  {
    path: 'profile/edit-shop', component: ProfileFormComponent, canActivate: [sellerGuard, canEditApprovedGuard], data: {mode: 'edit-approved'},
  },
  {
    path: '**', component: NotFoundComponent,
  }
];
