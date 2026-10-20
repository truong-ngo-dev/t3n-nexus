import { Routes } from '@angular/router';
import { authenticatedGuard, guestOnlyGuard } from '@t3n/auth';
import { AccountComponent } from './pages/account/account.component';
import { HomeComponent } from './pages/home/home.component';
import { LoginComponent } from './pages/login/login.component';
import { NotFoundComponent } from './pages/not-found/not-found.component';
import { RegisterComponent } from './pages/register/register.component';

// `**` là trang không tìm thấy, không chuyển hướng (R16)
export const routes: Routes = [
  { path: '', component: HomeComponent, title: 'T3N Storefront' },
  { path: 'login', component: LoginComponent, canActivate: [guestOnlyGuard], title: 'Đăng nhập' },
  { path: 'register', component: RegisterComponent, canActivate: [guestOnlyGuard], title: 'Đăng ký' },
  { path: 'account', component: AccountComponent, canActivate: [authenticatedGuard], title: 'Tài khoản' },
  { path: '**', component: NotFoundComponent, title: 'Không tìm thấy trang' },
];
