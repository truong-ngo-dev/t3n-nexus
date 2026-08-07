import { Component, inject, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '@t3n/shared/data-access';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [RouterLink, MatButtonModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent implements OnInit {
  private readonly auth   = inject(AuthService);
  private readonly router = inject(Router);

  ngOnInit(): void {
    const user = this.auth.user();
    if (user) this.router.navigate([`/${user.role.toLowerCase()}`]);
  }

  login(): void {
    this.auth.login();
  }
}
