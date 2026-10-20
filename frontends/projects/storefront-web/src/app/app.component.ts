import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { NavigationCancel, Router, RouterLink, RouterOutlet } from '@angular/router';
import { SessionService } from '@t3n/auth';
import { filter } from 'rxjs';
import { ROLE_LABEL } from './shared/role-label';

/**
 * Khung trang: đầu trang (logo, nhãn vai, nút Đăng xuất) và vùng nội dung. Phiên `unknown` (lỗi) thay vùng
 * nội dung bằng màn lỗi kèm nút thử lại, không đi tiếp và không coi là khách (R10, tech.md T3).
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, MatToolbarModule, MatButtonModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  protected readonly sessionService = inject(SessionService);
  private readonly router = inject(Router);
  /** Địa chỉ bị guard hủy lúc phiên lỗi. Trình duyệt đã bị đưa về `/` nên không đọc lại được từ thanh địa chỉ. */
  private blockedUrl: string | null = null;

  protected readonly roleLabel = ROLE_LABEL;
  protected readonly session = computed(() => {
    const state = this.sessionService.state();
    return state.kind === 'authenticated' ? state.session : null;
  });

  protected readonly isGuest = computed(() => this.sessionService.state().kind === 'anonymous');

  protected readonly loggingOut = signal(false);
  protected readonly logoutFailed = signal(false);

  constructor() {
    this.router.events
      .pipe(filter((e): e is NavigationCancel => e instanceof NavigationCancel), takeUntilDestroyed())
      .subscribe((e) => (this.blockedUrl = e.url));
  }

  /** Khóa nút khi đang gửi (bấm đúp không gửi hai lần). Thất bại thì báo và mở lại nút; thành công thì trang đã điều hướng đi. */
  protected logout(): void {
    if (this.loggingOut()) return;
    this.loggingOut.set(true);
    this.logoutFailed.set(false);
    this.sessionService.logout().subscribe((result) => {
      if (result.kind === 'failed') {
        this.logoutFailed.set(true);
        this.loggingOut.set(false);
      }
    });
  }

  /** Thăm dò lại; được thì đi tiếp tới địa chỉ vừa bị chặn lúc lỗi (nếu có). */
  protected retry(): void {
    this.sessionService.probe().subscribe(() => {
      if (this.sessionService.state().kind === 'unknown') return;
      const target = this.blockedUrl;
      this.blockedUrl = null;
      if (target) void this.router.navigateByUrl(target);
    });
  }
}
