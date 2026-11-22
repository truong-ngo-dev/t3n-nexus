import {TestBed} from '@angular/core/testing';
import {Router, provideRouter} from '@angular/router';
import {Subject, TimeoutError, of, throwError} from 'rxjs';
import {SellerProfileApi} from '../../../../core/service/seller-profile/seller-profile.api';
import {SellerProfileCommandApi} from '../../data/seller-profile-command.api';
import {SellerStatusService} from '../../../../core/service/seller-profile/seller-status.service';
import {SellerProfile} from '../../../../core/service/seller-profile/seller-profile.model';
import {ProfileFormComponent} from './profile-form.component';

/*
 * K6 của `1-seller-profile/test.md` (ca trọng yếu): hết giờ khi gửi thì chưa rõ đã ghi chưa, nên giữ khóa nút, làm mới trạng thái,
 * rời về `leaveTo`, và KHÔNG có lời gọi thứ hai.
 */
const PROFILE: SellerProfile = {
  id: 's-1',
  status: 'APPROVED',
  sellerType: 'HOUSEHOLD',
  legalInfo: { legalName: 'Nguyễn Văn An', identityNumber: '001203004567', taxCode: '0123456789', businessAddress: '12 Bát Tràng, Gia Lâm, Hà Nội' },
  shop: { shopName: 'Gốm Minh An', description: 'Gốm thủ công', contact: { personName: 'Nguyễn Văn An', phone: '0912345678', address: null } },
};

describe('ProfileFormComponent: hết giờ khi gửi (K6)', () => {
  it('edit-approved: giữ khóa nút, refresh, về /profile, chỉ một lời gọi', async () => {
    const updateShopInfo = jest.fn(() => throwError(() => new TimeoutError()));
    const refreshed = new Subject<void>();
    const refresh = jest.fn(() => refreshed);

    TestBed.configureTestingModule({
      imports: [ProfileFormComponent],
      providers: [
        provideRouter([]),
        { provide: SellerProfileApi, useValue: { getMyProfile: () => of(PROFILE) } },
        { provide: SellerProfileCommandApi, useValue: { updateShopInfo } },
        { provide: SellerStatusService, useValue: { refresh } },
      ],
    });
    const navigate = jest.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    const fixture = TestBed.createComponent(ProfileFormComponent);
    fixture.componentRef.setInput('mode', 'edit-approved');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const host: HTMLElement = fixture.nativeElement;
    const description = host.querySelector<HTMLTextAreaElement>('[data-testid="description"]')!;
    description.value = 'Mô tả mới';
    description.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const submit = host.querySelector<HTMLButtonElement>('[data-testid="submit"]')!;
    expect(submit.disabled).toBe(false);
    submit.click();
    fixture.detectChanges();

    // Lỗi đã về nhưng trạng thái đang được làm mới: nút vẫn khóa, chưa rời trang.
    expect(updateShopInfo).toHaveBeenCalledTimes(1);
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(submit.disabled).toBe(true);
    expect(navigate).not.toHaveBeenCalled();

    // Bấm lại trong lúc đó không tạo lời gọi thứ hai.
    submit.click();
    expect(updateShopInfo).toHaveBeenCalledTimes(1);

    refreshed.next();
    refreshed.complete();
    expect(navigate).toHaveBeenCalledWith('/profile');
  });
});
