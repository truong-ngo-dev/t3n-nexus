import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {HttpErrorResponse, provideHttpClient} from '@angular/common/http';
import {TestBed} from '@angular/core/testing';
import {InvalidSellerProfileResponse, SELLER_PROFILE_URL, SellerApiService} from './seller-api.service';

function ok(data: unknown): object {
  return { success: true, data, message: null, errors: null, traceId: 't-1' };
}

describe('SellerApiService (lớp bọc HTTP của hồ sơ người bán)', () => {
  let api: SellerApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(SellerApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lấy hồ sơ bằng GET tới đúng địa chỉ qua BFF', () => {
    api.getMyProfile().subscribe();
    const req = http.expectOne(SELLER_PROFILE_URL);

    expect(req.request.method).toBe('GET');
    expect(req.request.url).toBe('/api/seller/seller/profile');
    req.flush(ok({ status: 'APPROVED' }));
  });

  it('bóc lớp bọc và trả đủ hồ sơ', () => {
    const data = {
      id: 'p-1',
      status: 'REJECTED',
      sellerType: 'INDIVIDUAL',
      legalInfo: { legalName: 'Nguyễn A' },
      shop: { shopName: 'Shop A', description: null },
      rejectionReason: 'Giấy tờ mờ',
    };
    let received: unknown;
    api.getMyProfile().subscribe((profile) => (received = profile));
    http.expectOne(SELLER_PROFILE_URL).flush(ok(data));

    expect(received).toEqual(data);
  });

  it.each([
    ['status lạ', ok({ status: 'SUSPENDED' })],
    ['thiếu status', ok({})],
    ['success là false', { success: false, data: { status: 'APPROVED' } }],
    ['data là null', ok(null)],
    ['thân là null', null],
  ])('200 sai khuôn (%s) phát InvalidSellerProfileResponse', (_name, body) => {
    const onError = jest.fn();
    api.getMyProfile().subscribe({ error: onError });
    http.expectOne(SELLER_PROFILE_URL).flush(body);

    expect(onError).toHaveBeenCalledTimes(1);
    expect(onError.mock.calls[0][0]).toBeInstanceOf(InvalidSellerProfileResponse);
  });

  it('lỗi HTTP cứ phát ra nguyên vẹn, không bị đổi kiểu', () => {
    const onError = jest.fn();
    api.getMyProfile().subscribe({ error: onError });
    http.expectOne(SELLER_PROFILE_URL).flush({ code: 'SELLER_PROFILE_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });

    expect(onError.mock.calls[0][0]).toBeInstanceOf(HttpErrorResponse);
    expect(onError.mock.calls[0][0].status).toBe(404);
  });
});
