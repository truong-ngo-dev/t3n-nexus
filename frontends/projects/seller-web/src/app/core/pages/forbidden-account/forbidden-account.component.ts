import { Component } from '@angular/core';

/** Đích cuối của tài khoản ADMIN (không gắn guard để tránh vòng lặp). Chỉ có nội dung tĩnh; nút đăng xuất đã có ở header. */
@Component({
  selector: 'app-forbidden-account',
  templateUrl: './forbidden-account.component.html',
  styleUrl: './forbidden-account.component.scss',
})
export class ForbiddenAccountComponent {}
