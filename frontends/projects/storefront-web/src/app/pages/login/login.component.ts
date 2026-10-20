import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { ActivatedRoute } from '@angular/router';
import { LOGIN_URL } from '@t3n/auth';

/**
 * Màn Đăng nhập. Đăng nhập là điều hướng cấp cao tới BFF (302 sang Xác thực), nên nút là liên kết thường,
 * không phải lời gọi fetch. `?error` (callback thất bại, không kèm lý do) chỉ hiện thông báo chung và KHÔNG tự đi
 * đăng nhập lại: tự chuyển sẽ vòng lặp (R5).
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [MatCardModule, MatButtonModule],
  templateUrl: './login.component.html',
})
export class LoginComponent {
  protected readonly loginUrl = LOGIN_URL;
  protected readonly failed = inject(ActivatedRoute).snapshot.queryParamMap.has('error');
}
