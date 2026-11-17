import { Component, inject } from '@angular/core';
import { SellerStatusService } from '../../shared/service/seller-status.service';

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent {
  private readonly sellerStatus = inject(SellerStatusService);

  // BINDING: tín hiệu chỉ đọc cho template. Template đọc `state()` rồi @switch theo `.kind`.
  // Chỉ có bốn trạng thái tới được đây (none, pending, rejected, approved); loading và unknown đã do khung xử lý.
  readonly state = this.sellerStatus.state;
}
