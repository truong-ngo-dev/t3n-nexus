import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatTabsModule } from '@angular/material/tabs';
import { PersonalInfoTabComponent } from '../personal-info-tab/personal-info-tab.component';

@Component({
  selector: 'app-account-profile',
  standalone: true,
  imports: [MatTabsModule, MatCardModule, PersonalInfoTabComponent],
  templateUrl: './account-profile.component.html',
  styleUrl: './account-profile.component.scss',
})
export class AccountProfileComponent {}
