import { Component, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatTabsModule } from '@angular/material/tabs';
import { MatIconModule } from '@angular/material/icon';
import { ActivatedRoute } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import { PersonalInfoTabComponent } from '../personal-info-tab/personal-info-tab.component';
import { SecurityTabComponent } from '../security-tab/security-tab.component';
import { ChangePasswordComponent } from '../change-password/change-password.component';

@Component({
  selector: 'app-account-profile',
  standalone: true,
  imports: [
    MatTabsModule,
    MatCardModule,
    MatIconModule,
    PersonalInfoTabComponent,
    SecurityTabComponent,
    ChangePasswordComponent
  ],
  templateUrl: './account-profile.component.html',
  styleUrl: './account-profile.component.scss',
})
export class AccountProfileComponent {
  private readonly route = inject(ActivatedRoute);
  
  // Track Level 1 tab (0: info, 1: security)
  protected readonly activeTab = toSignal(
    this.route.data.pipe(map(data => data['tab'] ?? 0)),
    { initialValue: 0 }
  );

  // Track Level 2 tab
  protected readonly activeSubTab = toSignal(
    this.route.data.pipe(map(data => data['subTab'] ?? 0)),
    { initialValue: 0 }
  );

  protected readonly pageTitle = toSignal(
    this.route.data.pipe(map(data => {
      const tab = data['tab'] ?? 0;
      const subTab = data['subTab'] ?? 0;
      
      if (tab === 0) {
        if (subTab === 0) return 'Thông tin tài khoản';
        if (subTab === 1) return 'Đổi mật khẩu';
        return 'Hồ sơ';
      } else {
        if (subTab === 0) return 'Thiết bị';
        return 'Lịch sử đăng nhập';
      }
    })),
    { initialValue: 'Thông tin tài khoản' }
  );

  protected readonly parentTitle = toSignal(
    this.route.data.pipe(map(data => (data['tab'] ?? 0) === 0 ? 'Thông tin cá nhân' : 'Quản lý truy cập')),
    { initialValue: 'Thông tin cá nhân' }
  );
}
