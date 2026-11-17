import {Component, ChangeDetectionStrategy, signal, inject, computed} from '@angular/core';
import {Location} from '@angular/common';
import {Router, RouterOutlet} from '@angular/router';
import {SessionService} from '@t3n/auth';
import {SellerBootstrapService} from './shared/service/seller-bootstrap.service';
import {SellerStatusService} from './shared/service/seller-status.service';
import {STATUS_LABEL} from './shared/status-label';
import {MatToolbar} from '@angular/material/toolbar';
import {MatButton} from '@angular/material/button';
import {MatProgressSpinner} from '@angular/material/progress-spinner';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, MatToolbar, MatButton, MatProgressSpinner],
  templateUrl: './app.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './app.component.scss',
})
export class AppComponent {
  private readonly session = inject(SessionService);
  private readonly sellerStatus = inject(SellerStatusService);
  private readonly bootstrap = inject(SellerBootstrapService);
  private readonly router = inject(Router);
  private readonly location = inject(Location);

  readonly isAuthenticated = computed(() => this.session.state().kind === 'authenticated');

  // Nhãn trạng thái hồ sơ chỉ có nghĩa với USER: hồ sơ của ADMIN không được nạp và đứng ở "đang kiểm tra" mãi.
  readonly showStatus = computed(() => {
    const session = this.session.state();
    return session.kind === 'authenticated' && session.session.role === 'USER';
  });

  // Cặp ghi/đọc: bên ngoài chỉ đọc được, muốn đổi phải gọi logout() hoặc retry().
  private readonly _loggingOut = signal(false);
  private readonly _logoutError = signal<string | null>(null);
  private readonly _retrying = signal(false);

  readonly loggingOut = this._loggingOut.asReadonly();
  readonly logoutError = this._logoutError.asReadonly();
  readonly retrying = this._retrying.asReadonly();

  logout(): void {
    if (this._loggingOut()) return;
    this._loggingOut.set(true);
    this._logoutError.set(null);
    this.session.logout().subscribe({
      next: (result) => {
        // redirected: trang sắp rời đi nên giữ khóa; chỉ mở khóa khi thất bại.
        if (result.kind === 'failed') {
          this._loggingOut.set(false);
          this._logoutError.set('Đăng xuất thất bại, vui lòng thử lại');
        }
      },
    });
  }

  retry(): void {
    if (this._retrying()) return;
    this._retrying.set(true);
    const reload = this.session.state().kind === 'unknown' ? this.bootstrap.load() : this.sellerStatus.refresh();
    reload.subscribe({
      complete: () => {
        this._retrying.set(false);
        const stillBroken = this.session.state().kind === 'unknown' || this.sellerStatus.state().kind === 'unknown';
        if (!stillBroken) {
          // Lần điều hướng đầu bị guard hủy lúc lỗi và router không tự thử lại: yêu cầu vào lại URL hiện tại.
          void this.router.navigateByUrl(this.location.path() || '/', { onSameUrlNavigation: 'reload' });
        }
      },
    });
  }

  checkScreen(): 'loading' | 'error' | 'ok'  {
    const session = this.session.state();
    if (session.kind === 'loading') return 'loading';
    if (session.kind === 'unknown') return 'error';
    if (session.kind === 'authenticated' && session.session.role === 'USER') {
      const sellerStatus = this.sellerStatus.state().kind;
      if (sellerStatus === 'loading') return 'loading';
      if (sellerStatus === 'unknown') return 'error';
    }
    return 'ok'
  }

  getStatusLabel() {
    const kind = this.sellerStatus.state().kind;
    return STATUS_LABEL[kind];
  }
}
