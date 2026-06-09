import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '@t3n/shared/data-access';
import { UserAvatarComponent } from '@t3n/shared/ui';

@Component({
  selector: 'app-storefront-header',
  standalone: true,
  imports: [RouterLink, MatButtonModule, UserAvatarComponent],
  templateUrl: './storefront-header.component.html',
  styleUrl: './storefront-header.component.scss',
})
export class StorefrontHeaderComponent {
  protected readonly auth = inject(AuthService);
}
