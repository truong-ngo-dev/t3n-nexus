import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { IdentityService } from '@t3n/shared/data-access';
import { DeviceItem } from '@t3n/shared/model';

@Component({
  selector: 'app-device-list',
  standalone: true,
  imports: [DatePipe, MatButtonModule, MatDialogModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './device-list.component.html',
  styleUrl: './device-list.component.scss',
})
export class DeviceListComponent implements OnInit {
  private readonly identityService = inject(IdentityService);
  private readonly snackBar        = inject(MatSnackBar);
  private readonly dialog          = inject(MatDialog);

  protected readonly loading   = signal(true);
  protected readonly devices   = signal<DeviceItem[]>([]);
  protected readonly revoking  = signal<string | null>(null);

  ngOnInit(): void {
    this.loadDevices();
  }

  confirmRevoke(device: DeviceItem): void {
    const confirmed = window.confirm(
      `Thu hồi quyền truy cập của thiết bị "${device.displayName}"?`
    );
    if (confirmed) {
      this.revoke(device.deviceId);
    }
  }

  private revoke(deviceId: string): void {
    this.revoking.set(deviceId);
    this.identityService.revokeDevice(deviceId).subscribe({
      next: () => {
        this.devices.update(list => list.filter(d => d.deviceId !== deviceId));
        this.revoking.set(null);
        this.snackBar.open('Đã thu hồi quyền truy cập', undefined, { duration: 3000 });
      },
      error: () => {
        this.revoking.set(null);
        this.snackBar.open('Thao tác thất bại, vui lòng thử lại', undefined, { duration: 3000 });
      },
    });
  }

  private loadDevices(): void {
    this.identityService.getDevices().subscribe({
      next: list => {
        this.devices.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  deviceIcon(browser: string): string {
    const b = browser?.toLowerCase() ?? '';
    if (b.includes('mobile') || b.includes('android') || b.includes('ios')) return 'smartphone';
    if (b.includes('tablet') || b.includes('ipad')) return 'tablet';
    return 'computer';
  }
}
