import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { IdentityService } from '@t3n/shared/data-access';

type VerifyState = 'loading' | 'success' | 'error';

@Component({
  selector: 'app-verify-email',
  standalone: true,
  imports: [RouterLink, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './verify-email.component.html',
  styleUrl: './verify-email.component.scss',
})
export class VerifyEmailComponent implements OnInit {
  private readonly route    = inject(ActivatedRoute);
  private readonly router   = inject(Router);
  private readonly identity = inject(IdentityService);

  protected readonly state        = signal<VerifyState>('loading');
  protected readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.state.set('error');
      this.errorMessage.set('Link xác nhận không hợp lệ.');
      return;
    }

    this.identity.verifyEmail(token).subscribe({
      next: () => this.state.set('success'),
      error: (err: HttpErrorResponse) => {
        // 409 = email đã được xác thực từ trước (VD bấm lại link cũ) — với user, kết quả cuối
        // (tài khoản đã kích hoạt) giống hệt success, không cần hiện như lỗi.
        if (err.status === 409) {
          this.state.set('success');
          return;
        }
        this.state.set('error');
        this.errorMessage.set(
          err.status === 410
            ? 'Link xác nhận đã hết hạn. Vui lòng đăng ký lại hoặc liên hệ hỗ trợ.'
            : 'Link xác nhận không hợp lệ hoặc đã được sử dụng.'
        );
      },
    });
  }

  goToLogin(): void {
    this.router.navigate(['/login']);
  }
}
