import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute } from '@angular/router';
import { LOGIN_URL } from '@t3n/auth';

/**
 * Trang đăng nhập. Đăng nhập là điều hướng cấp cao tới BFF, nên nút là liên kết thường (không gọi fetch).
 * `?error` (callback thất bại, không kèm lý do) chỉ hiện thông báo chung và KHÔNG tự đi đăng nhập lại: tự chuyển sẽ vòng lặp (R5).
 */
@Component({
  selector: 'app-login',
  imports: [MatButtonModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  protected readonly loginUrl = LOGIN_URL;
  protected readonly failed = inject(ActivatedRoute).snapshot.queryParamMap.has('error');
}
