import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { IdentityService } from '@t3n/shared/data-access';
import { LoginHistoryItem } from '@t3n/shared/model';

@Component({
  selector: 'app-login-history',
  standalone: true,
  imports: [DatePipe, MatIconModule, MatProgressSpinnerModule, MatPaginatorModule],
  templateUrl: './login-history.component.html',
  styleUrl: './login-history.component.scss',
})
export class LoginHistoryComponent implements OnInit {
  private readonly identityService = inject(IdentityService);

  protected readonly loading       = signal(true);
  protected readonly items         = signal<LoginHistoryItem[]>([]);
  protected readonly totalElements = signal(0);
  protected readonly pageIndex     = signal(0);
  protected readonly pageSize      = signal(5);

  protected readonly pageSizeOptions = [5, 10, 20];

  ngOnInit(): void {
    this.loadPage(0, this.pageSize());
  }

  onPageChange(event: PageEvent): void {
    this.loadPage(event.pageIndex, event.pageSize);
  }

  private loadPage(pageIndex: number, pageSize: number): void {
    this.loading.set(true);
    this.identityService.getLoginHistory(pageIndex, pageSize).subscribe({
      next: res => {
        this.items.set(res.content);
        this.totalElements.set(res.totalElements);
        this.pageIndex.set(res.page);
        this.pageSize.set(res.size);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      },
    });
  }

  actionIcon(action: string): string {
    switch (action?.toUpperCase()) {
      case 'SUCCESS':        return 'login';
      case 'WRONG_PASSWORD': return 'key_off';
      case 'ACCOUNT_LOCKED': return 'lock';
      case 'MFA_FAILED':     return 'phonelink_lock';
      default:               return 'history';
    }
  }

  actionLabel(action: string): string {
    switch (action?.toUpperCase()) {
      case 'SUCCESS':        return 'Đăng nhập thành công';
      case 'WRONG_PASSWORD': return 'Sai mật khẩu';
      case 'ACCOUNT_LOCKED': return 'Tài khoản bị khóa';
      case 'MFA_FAILED':     return 'Xác thực 2 bước thất bại';
      default:               return action;
    }
  }

  isFailure(action: string): boolean {
    const a = action?.toUpperCase();
    return a === 'WRONG_PASSWORD' || a === 'ACCOUNT_LOCKED' || a === 'MFA_FAILED';
  }
}
