import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {HttpErrorResponse, provideHttpClient} from '@angular/common/http';
import {TimeoutError} from 'rxjs';
import {DEFAULT_REQUEST_TIMEOUT_MS, InvalidApiResponse, classifyFailure} from '@t3n/http';
import {TestBed} from '@angular/core/testing';
import {SELLER_PROFILE_URL, SellerProfileApi} from './seller-profile.api';

function ok(data: unknown): object {
  return { code: 'OK', data, message: null, errors: null, traceId: 't-1' };
}

describe('SellerProfileApi (lời gọi hồ sơ người bán của tôi)', () => {
  let api: SellerProfileApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(SellerProfileApi);
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
    ['status lạ', ok({ status: 'SUSPENDED' }), 'data-mismatch'],
    ['thiếu status', ok({}), 'data-mismatch'],
    ['data là null', ok(null), 'data-mismatch'],
    ['thân là null', null, 'not-envelope'],
    ['thân là chuỗi (trang HTML)', '<html>Đăng nhập</html>', 'not-envelope'],
  ])('200 sai khuôn (%s) phát InvalidApiResponse lý do %s', (_name, body, reason) => {
    const onError = jest.fn();
    api.getMyProfile().subscribe({ error: onError });
    http.expectOne(SELLER_PROFILE_URL).flush(body);

    expect(onError).toHaveBeenCalledTimes(1);
    expect(onError.mock.calls[0][0]).toBeInstanceOf(InvalidApiResponse);
    expect(onError.mock.calls[0][0].reason).toBe(reason);
  });

  describe('hạn chờ của lệnh đọc hồ sơ', () => {
    beforeEach(() => jest.useFakeTimers());
    afterEach(() => jest.useRealTimers());

    it('chưa có phản hồi sau hạn mặc định: phát TimeoutError (classifyFailure xếp unknown-outcome) và hủy lời gọi', () => {
      const onError = jest.fn();
      api.getMyProfile().subscribe({ error: onError });

      jest.advanceTimersByTime(DEFAULT_REQUEST_TIMEOUT_MS);

      expect(onError.mock.calls[0][0]).toBeInstanceOf(TimeoutError);
      expect(classifyFailure(onError.mock.calls[0][0])).toBe('unknown-outcome');
      expect(http.match(SELLER_PROFILE_URL)[0].cancelled).toBe(true);
    });
  });

  it('lỗi HTTP cứ phát ra nguyên vẹn, không bị đổi kiểu', () => {
    const onError = jest.fn();
    api.getMyProfile().subscribe({ error: onError });
    http.expectOne(SELLER_PROFILE_URL).flush({ code: 'SELLER_PROFILE_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });

    expect(onError.mock.calls[0][0]).toBeInstanceOf(HttpErrorResponse);
    expect(onError.mock.calls[0][0].status).toBe(404);
  });
});
