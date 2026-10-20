import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { RouterLink } from '@angular/router';
import { SessionService } from '@t3n/auth';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [MatCardModule, MatButtonModule, RouterLink],
  templateUrl: './home.component.html',
})
export class HomeComponent {
  private readonly sessionService = inject(SessionService);
  protected readonly isGuest = computed(() => this.sessionService.state().kind === 'anonymous');
}
