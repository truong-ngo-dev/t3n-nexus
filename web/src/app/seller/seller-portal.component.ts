import { Component, inject } from '@angular/core';
import { AuthService } from '@t3n/shared/data-access';

@Component({
  selector: 'app-seller-portal',
  standalone: true,
  templateUrl: './seller-portal.component.html',
  styleUrl: './seller-portal.component.scss',
})
export class SellerPortalComponent {
  protected readonly auth = inject(AuthService);
}
