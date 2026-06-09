import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { AuthService } from '@t3n/shared/data-access';

@Component({
  selector: 'app-account-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatListModule, MatIconModule],
  templateUrl: './account-shell.component.html',
  styleUrl: './account-shell.component.scss',
})
export class AccountShellComponent {
  protected readonly auth = inject(AuthService);
}
