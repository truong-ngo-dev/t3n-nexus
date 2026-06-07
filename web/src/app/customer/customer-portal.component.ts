import { Component, inject } from '@angular/core';
import { AuthService } from '@t3n/shared/data-access';

@Component({
  selector: 'app-customer-portal',
  standalone: true,
  template: `
    <div class="portal-screen">
      <h1>Customer Portal</h1>
      <button (click)="logout()">Logout</button>
    </div>
  `,
  styles: [`
    .portal-screen {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 24px;
      height: 100vh;
    }
    button { padding: 10px 28px; font-size: 14px; cursor: pointer; }
  `],
})
export class CustomerPortalComponent {

  private readonly auth = inject(AuthService);

  ngOnInit(): void {
    this.auth.checkSession().subscribe({
      error: () => { window.location.href = '/webgw/auth/login'; }
    });
  }

  logout(): void { this.auth.logout(); }
}
