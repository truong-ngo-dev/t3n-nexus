import {inject, Injectable} from '@angular/core';
import {SessionService} from '@t3n/auth';
import {SellerStatusService} from './seller-status.service';
import {Observable, of, switchMap} from 'rxjs';


@Injectable({ providedIn: 'root' })
export class SellerBootstrapService {
  private readonly session = inject(SessionService);
  private readonly seller = inject(SellerStatusService);

  /** Thăm dò phiên, rồi chỉ khi là USER mới nạp hồ sơ. Luồng luôn hoàn tất, không phát lỗi. */
  load(): Observable<void> {
    return this.session.probe().pipe(
      switchMap(() => {
        const session = this.session.state();
        const isUser = session.kind === 'authenticated' && session.session.role === 'USER';
        return isUser ? this.seller.refresh() : of(undefined);
      }),
    );
  }
}
