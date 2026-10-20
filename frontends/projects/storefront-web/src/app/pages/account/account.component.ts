import { Component, computed, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { SessionService } from '@t3n/auth';
import { ROLE_LABEL } from '../../shared/role-label';

@Component({
  selector: 'app-account',
  standalone: true,
  imports: [MatCardModule],
  templateUrl: './account.component.html',
})
export class AccountComponent {
  private readonly sessionService = inject(SessionService);
  protected readonly roleLabel = ROLE_LABEL;
  protected readonly session = computed(() => {
    const state = this.sessionService.state();
    return state.kind === 'authenticated' ? state.session : null;
  });
}
