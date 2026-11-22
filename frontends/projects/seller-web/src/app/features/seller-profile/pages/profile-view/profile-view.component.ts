import {Component, DestroyRef, OnInit, computed, inject, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {RouterLink} from '@angular/router';
import {MatButton} from '@angular/material/button';
import {MatProgressSpinner} from '@angular/material/progress-spinner';
import {SellerStatusService} from '../../../../core/service/seller-profile/seller-status.service';
import {STATUS_LABEL} from '../../../../core/service/seller-profile/seller-status.labels';
import {SellerProfile, SellerType} from '../../../../core/service/seller-profile/seller-profile.model';
import {ProfileFormField, fieldLabel, formValueFromProfile, legalFieldsFor} from '../../profile-form';

/** Tên loại hình hiện cho người dùng. */
const TYPE_LABEL: Record<SellerType, string> = {
  INDIVIDUAL: 'Cá nhân',
  HOUSEHOLD: 'Hộ kinh doanh',
  BUSINESS: 'Doanh nghiệp',
};

/** Một dòng "nhãn: giá trị" của trang xem. */
export interface ProfileRow {
  field: ProfileFormField;
  label: string;
  value: string;
}

/**
 * Trang xem hồ sơ của chính người bán (`/profile`). Hồ sơ đầy đủ do trang tự giữ; `SellerStatusService` chỉ giữ trạng thái.
 * Đọc qua `sellerStatus.loadProfile()` nên trạng thái dùng chung cũng được làm mới theo chính phản hồi này, không tốn thêm lời gọi.
 */
@Component({
  selector: 'app-profile-view',
  imports: [RouterLink, MatButton, MatProgressSpinner],
  templateUrl: './profile-view.component.html',
  styleUrl: './profile-view.component.scss',
})
export class ProfileViewComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly sellerStatus = inject(SellerStatusService);

  private readonly _loadState = signal<'loading' | 'loaded' | 'error'>('loading');
  private readonly _profile = signal<SellerProfile | null>(null);

  readonly loadState = this._loadState.asReadonly();
  readonly profile = this._profile.asReadonly();

  readonly statusLabel = computed(() => STATUS_LABEL[this.sellerStatus.state().kind]);

  /** Loại hình bằng chữ, cho dòng đầu của trang. */
  readonly typeLabel = computed(() => {
    const type = this._profile()?.sellerType;
    return type ? TYPE_LABEL[type] : '';
  });

  /** Các dòng hiện ra, đúng thứ tự và nhãn của biểu mẫu: pháp lý (theo loại hình), gian hàng, liên hệ. Ô tùy chọn trống thì bỏ. */
  readonly rows = computed<ProfileRow[]>(() => {
    const profile = this._profile();
    if (!profile) return [];
    const value = formValueFromProfile(profile);
    const fields: ProfileFormField[] = [
      ...legalFieldsFor(profile.sellerType),
      'shopName', 'description', 'contactPersonName', 'contactPhone', 'contactAddress',
    ];
    return fields
      .filter((field) => value[field] !== '')
      .map((field) => ({field, label: fieldLabel(profile.sellerType, field), value: String(value[field])}));
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this._loadState.set('loading');
    this.sellerStatus.loadProfile()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (profile) => {
          this._profile.set(profile);
          this._loadState.set('loaded');
        },
        error: () => this._loadState.set('error'),
      });
  }
}
