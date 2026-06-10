import { Component, input, signal, effect } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { LoginHistoryComponent } from '../login-history/login-history.component';
import { DeviceListComponent } from '../device-list/device-list.component';

@Component({
  selector: 'app-security-tab',
  standalone: true,
  imports: [MatTabsModule, LoginHistoryComponent, DeviceListComponent],
  templateUrl: './security-tab.component.html',
  styleUrl: './security-tab.component.scss',
})
export class SecurityTabComponent {
  initialSubTab = input<number>(0);
  
  protected readonly historyLoaded = signal(false);
  protected readonly devicesLoaded = signal(true);

  constructor() {
    effect(() => {
      const subTab = this.initialSubTab();
      if (subTab === 1) this.historyLoaded.set(true);
    });
  }

  onSubTabChange(index: number): void {
    if (index === 1) this.historyLoaded.set(true);
  }
}
