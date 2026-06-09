import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { StorefrontHeaderComponent } from '@t3n/storefront/ui';

@Component({
  selector: 'app-customer-portal',
  standalone: true,
  imports: [RouterOutlet, StorefrontHeaderComponent],
  templateUrl: './customer-portal.component.html',
  styleUrl: './customer-portal.component.scss',
})
export class CustomerPortalComponent {}
