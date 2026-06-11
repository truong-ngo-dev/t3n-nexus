import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatBottomSheet } from '@angular/material/bottom-sheet';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { IdentityService } from '@t3n/shared/data-access';
import { DeviceItem } from '@t3n/shared/model';
import { DeviceDetailSheetComponent, DeviceDetailSheetData } from '../device-detail-sheet/device-detail-sheet.component';

@Component({
  selector:    'app-device-list',
  standalone:  true,
  imports:     [DatePipe, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './device-list.component.html',
  styleUrl:    './device-list.component.scss',
})
export class DeviceListComponent implements OnInit {
  private readonly identityService = inject(IdentityService);
  private readonly bottomSheet     = inject(MatBottomSheet);

  protected readonly loading = signal(true);
  protected readonly devices = signal<DeviceItem[]>([]);

  protected readonly currentDeviceTrusted = computed(
    () => this.devices().find(d => d.isCurrent)?.isTrusted ?? false
  );

  ngOnInit(): void {
    this.loadDevices();
  }

  protected openDetail(device: DeviceItem): void {
    const data: DeviceDetailSheetData = {
      device,
      currentDeviceTrusted: this.currentDeviceTrusted(),
    };
    const ref = this.bottomSheet.open(DeviceDetailSheetComponent, { data, panelClass: 'white-bottom-sheet' });
    ref.afterDismissed().subscribe((result: string | undefined) => {
      if (result === 'refreshed') this.loadDevices();
    });
  }

  protected deviceIcon(browser: string): string {
    const b = browser?.toLowerCase() ?? '';
    if (b.includes('mobile') || b.includes('android') || b.includes('ios')) return 'smartphone';
    if (b.includes('tablet') || b.includes('ipad')) return 'tablet';
    return 'computer';
  }

  private loadDevices(): void {
    this.loading.set(true);
    this.identityService.getDevices().subscribe({
      next: list => {
        this.devices.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
