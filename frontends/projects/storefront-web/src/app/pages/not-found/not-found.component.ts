import { Component, ChangeDetectionStrategy } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [MatCardModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './not-found.component.html',
})
export class NotFoundComponent {}
