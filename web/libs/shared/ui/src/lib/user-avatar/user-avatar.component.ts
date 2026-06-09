import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { AuthService } from '@t3n/shared/data-access';

@Component({
  selector: 'app-user-avatar',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, MatMenuModule],
  templateUrl: './user-avatar.component.html',
  styleUrl: './user-avatar.component.scss',
})
export class UserAvatarComponent {
  protected readonly auth    = inject(AuthService);
  private   readonly router  = inject(Router);

  protected readonly initials = computed(() => {
    const name = this.auth.user()?.fullName ?? '';
    return name.trim() ? name.trim()[0].toUpperCase() : '';
  });

  goToProfile(): void {
    this.router.navigate(['/customer/account/profile']);
  }
}
