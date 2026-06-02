import { Component, inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '@t3n/shared/data-access';

@Component({
  selector: 'app-login',
  standalone: true,
  template: `
    <div class="login-screen">
      <button (click)="login()">Login</button>
    </div>
  `,
  styles: [`
    .login-screen {
      display: flex;
      justify-content: center;
      align-items: center;
      height: 100vh;
    }
    button {
      padding: 12px 32px;
      font-size: 16px;
      cursor: pointer;
    }
  `],
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
