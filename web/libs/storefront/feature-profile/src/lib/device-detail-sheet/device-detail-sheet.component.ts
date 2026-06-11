import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_BOTTOM_SHEET_DATA, MatBottomSheetRef } from '@angular/material/bottom-sheet';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { IdentityService } from '@t3n/shared/data-access';
import { DeviceItem } from '@t3n/shared/model';

export interface DeviceDetailSheetData {
  device:               DeviceItem;
  currentDeviceTrusted: boolean;
}

type TrustState = 'idle' | 'requesting' | 'otp_input' | 'verifying' | 'success';

@Component({
  selector:    'app-device-detail-sheet',
  standalone:  true,
  imports:     [DatePipe, FormsModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './device-detail-sheet.component.html',
  styleUrl:    './device-detail-sheet.component.scss',
})
export class DeviceDetailSheetComponent {
  private readonly sheetRef        = inject(MatBottomSheetRef<DeviceDetailSheetComponent>);
  private readonly identityService = inject(IdentityService);
  private readonly snackBar        = inject(MatSnackBar);

  protected readonly data: DeviceDetailSheetData = inject(MAT_BOTTOM_SHEET_DATA);

  protected readonly trustState       = signal<TrustState>('idle');
  protected readonly otpValue         = signal('');
  protected readonly remoteLoggingOut = signal(false);
  protected readonly untrusting       = signal(false);

  protected readonly device               = computed(() => this.data.device);
  protected readonly currentDeviceTrusted = computed(() => this.data.currentDeviceTrusted);

  protected deviceIcon(): string {
    const b = this.device().browser?.toLowerCase() ?? '';
    if (b.includes('mobile') || b.includes('android') || b.includes('ios')) return 'smartphone';
    if (b.includes('tablet') || b.includes('ipad')) return 'tablet';
    return 'computer';
  }

  protected requestOtp(): void {
    this.trustState.set('requesting');
    this.identityService.requestDeviceTrustOtp(this.device().deviceId).subscribe({
      next: () => {
        this.trustState.set('otp_input');
        this.otpValue.set('');
      },
      error: (err) => {
        this.trustState.set('idle');
        const code = err?.error?.errorCode;
        const msg  = code === 12009
          ? 'Đã gửi quá nhiều yêu cầu, vui lòng thử lại sau'
          : 'Không thể gửi mã OTP, vui lòng thử lại';
        this.snackBar.open(msg, undefined, { duration: 4000 });
      },
    });
  }

  protected verifyOtp(): void {
    const otp = this.otpValue();
    if (otp.length !== 6) return;
    this.trustState.set('verifying');
    this.identityService.verifyDeviceTrustOtp(this.device().deviceId, otp).subscribe({
      next: () => {
        this.trustState.set('success');
        setTimeout(() => this.sheetRef.dismiss('refreshed'), 1200);
      },
      error: (err) => {
        this.trustState.set('otp_input');
        const code = err?.error?.errorCode;
        const msg  = code === 12010 ? 'Mã OTP đã hết hạn'
                   : code === 12011 ? 'Mã OTP không đúng'
                   : 'Xác thực thất bại, vui lòng thử lại';
        this.snackBar.open(msg, undefined, { duration: 4000 });
      },
    });
  }

  protected untrustDevice(): void {
    this.untrusting.set(true);
    this.identityService.untrustDevice(this.device().deviceId).subscribe({
      next: () => {
        this.untrusting.set(false);
        this.sheetRef.dismiss('refreshed');
      },
      error: () => {
        this.untrusting.set(false);
        this.snackBar.open('Thao tác thất bại, vui lòng thử lại', undefined, { duration: 3000 });
      },
    });
  }

  protected remoteLogout(): void {
    const sessionId = this.device().sessionId;
    if (!sessionId) return;
    this.remoteLoggingOut.set(true);
    this.identityService.remoteLogout(sessionId).subscribe({
      next: () => {
        this.remoteLoggingOut.set(false);
        this.sheetRef.dismiss('refreshed');
      },
      error: () => {
        this.remoteLoggingOut.set(false);
        this.snackBar.open('Thao tác thất bại, vui lòng thử lại', undefined, { duration: 3000 });
      },
    });
  }

  protected onOtpInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.otpValue.set(input.value.replace(/\D/g, '').slice(0, 6));
  }

  protected close(): void {
    this.sheetRef.dismiss();
  }
}
