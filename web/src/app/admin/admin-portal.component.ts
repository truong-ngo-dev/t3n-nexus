import { Component, inject } from '@angular/core';
import { AuthService } from '@t3n/shared/data-access';

@Component({
  selector: 'app-admin-portal',
  standalone: true,
  templateUrl: './admin-portal.component.html',
  styleUrl: './admin-portal.component.scss',
})
export class AdminPortalComponent {
  protected readonly auth = inject(AuthService);
}
