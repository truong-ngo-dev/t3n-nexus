import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {HttpErrorResponse, provideHttpClient} from '@angular/common/http';
import {TimeoutError} from 'rxjs';
import {DEFAULT_REQUEST_TIMEOUT_MS, classifyFailure} from '@t3n/http';
import {TestBed} from '@angular/core/testing';
import {
  SELLER_PROFILE_RESUBMIT_URL,
  SELLER_PROFILE_SHOP_INFO_URL,
  SELLER_PROFILE_URL,
} from '../../../core/service/seller-profile/seller-profile.api';
import {SellerProfileCommandApi} from './seller-profile-command.api';
import {SubmitSellerProfileRequest} from './seller-profile-command.model';

function ok(data: unknown): object {
  return { code: 'OK', data, message: null, errors: null, traceId: 't-1' };
}

describe('SellerProfileCommandApi (lệnh ghi hồ sơ người bán của tôi)', () => {
  let api: SellerProfileCommandApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(SellerProfileCommandApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /**
   * Nộp (`POST /profile`, 201) và nộp lại (`POST /profile/resubmit`, 200) cùng một khuôn yêu cầu. Thân phản hồi **không được dùng**
   * (sau khi nộp, trạng thái được tải lại bằng GET), nên dịch vụ chỉ báo thành công hoặc lỗi: `Observable<void>`. Không đọc thân (`ignoreBody`): 2xx là thành công; có hạn chờ (`requestTimeout`).
   */
  describe.each([
    { name: 'submit', url: SELLER_PROFILE_URL, expectedUrl: '/api/seller/seller/profile', successStatus: 201 },
    { name: 'resubmit', url: SELLER_PROFILE_RESUBMIT_URL, expectedUrl: '/api/seller/seller/profile/resubmit', successStatus: 200 },
  ] as const)('$name', ({ name, url, expectedUrl, successStatus }) => {
    const request: SubmitSellerProfileRequest = {
      sellerType: 'BUSINESS',
      legalInfo: {
        legalName: 'Công ty A',
        representativeName: 'Nguyễn A',
        identityNumber: '079123456789',
        taxCode: '0123456789',
        businessAddress: '12 Lê Lợi, Quận 1, TP.HCM',
      },
      shop: { shopName: 'Shop A', description: null, contact: { personName: 'Nguyễn A', phone: '0901234567', address: null } },
    };

    const call = () => api[name](request);

    it('chưa subscribe thì chưa gửi gì (luồng lạnh)', () => {
      call();
      http.expectNone(url);
    });

    it('gửi bằng POST tới đúng địa chỉ qua BFF, thân là nguyên yêu cầu (không thêm, bớt, đổi tên trường)', () => {
      call().subscribe();
      const req = http.expectOne(url);

      expect(req.request.method).toBe('POST');
      expect(req.request.url).toBe(expectedUrl);
      expect(req.request.body).toEqual(request);
      req.flush(ok({ id: 'p-1', status: 'PENDING_REVIEW' }), { status: successStatus, statusText: 'OK' });
    });

    it('thành công: phát đúng một giá trị rỗng (void) rồi hoàn tất, không lộ thân phản hồi', () => {
      const onNext = jest.fn();
      const onComplete = jest.fn();
      call().subscribe({ next: onNext, complete: onComplete });
      http.expectOne(url).flush(ok({ id: 'p-1', status: 'PENDING_REVIEW' }), { status: successStatus, statusText: 'OK' });

      expect(onNext).toHaveBeenCalledTimes(1);
      expect(onNext).toHaveBeenCalledWith(undefined);
      expect(onComplete).toHaveBeenCalledTimes(1);
    });

    it('không đọc data: data sai khuôn hay vắng vẫn là thành công', () => {
      const onError = jest.fn();
      const onComplete = jest.fn();
      call().subscribe({ error: onError, complete: onComplete });
      http.expectOne(url).flush({ code: 'OK' }, { status: successStatus, statusText: 'OK' });

      expect(onError).not.toHaveBeenCalled();
      expect(onComplete).toHaveBeenCalledTimes(1);
    });

    it.each([
      ['thân null', null],
      ['thân là chuỗi', 'ok'],
      ['thân là đối tượng không có code', { data: { status: 'PENDING_REVIEW' } }],
      ['thân mang mã khác OK', { code: 'SOMETHING_ELSE', message: 'x' }],
    ])('2xx là thành công, không đọc thân lại (%s): hoàn tất, không phát lỗi', (_name, body) => {
      const onError = jest.fn();
      const onComplete = jest.fn();
      call().subscribe({ error: onError, complete: onComplete });
      http.expectOne(url).flush(body, { status: successStatus, statusText: 'OK' });

      expect(onError).not.toHaveBeenCalled();
      expect(onComplete).toHaveBeenCalledTimes(1);
    });
    it('lỗi HTTP phát ra nguyên vẹn, giữ cả mã lỗi và errors để biểu mẫu gắn vào đúng ô', () => {
      const onError = jest.fn();
      const body = { success: false, code: 'SELLER_FIELD_INVALID', message: 'Trường không hợp lệ: shopName', errors: ['shopName'] };
      call().subscribe({ error: onError });
      http.expectOne(url).flush(body, { status: 400, statusText: 'Bad Request' });

      const error = onError.mock.calls[0][0];
      expect(error).toBeInstanceOf(HttpErrorResponse);
      expect(error.status).toBe(400);
      expect(error.error).toEqual(body);
    });

    it('mỗi lần subscribe gửi một lời gọi, không thử lại ngầm khi lỗi', () => {
      call().subscribe({ error: () => undefined });
      http.expectOne(url).flush({}, { status: 503, statusText: 'Service Unavailable' });
      http.expectNone(url);
    });

    describe('hạn chờ', () => {
      beforeEach(() => jest.useFakeTimers());
      afterEach(() => jest.useRealTimers());

      it('chưa có phản hồi sau hạn mặc định: phát TimeoutError và hủy lời gọi (kết quả chưa rõ, không phải lỗi bị từ chối)', () => {
        const onError = jest.fn();
        call().subscribe({ error: onError });

        jest.advanceTimersByTime(DEFAULT_REQUEST_TIMEOUT_MS - 1);
        expect(onError).not.toHaveBeenCalled();

        jest.advanceTimersByTime(1);
        expect(onError).toHaveBeenCalledTimes(1);
        expect(onError.mock.calls[0][0]).toBeInstanceOf(TimeoutError);
        expect(classifyFailure(onError.mock.calls[0][0])).toBe('unknown-outcome');

        // Lời gọi bị hủy (unsubscribe) chứ không để treo; match() cũng gỡ nó khỏi danh sách đang mở để verify() không báo thừa
        const [pending] = http.match(url);
        expect(pending.cancelled).toBe(true);
      });

      it('phản hồi kịp hạn thì không có TimeoutError sau đó', () => {
        const onError = jest.fn();
        call().subscribe({ error: onError });

        jest.advanceTimersByTime(DEFAULT_REQUEST_TIMEOUT_MS - 1);
        http.expectOne(url).flush(ok(null), { status: successStatus, statusText: 'OK' });
        jest.advanceTimersByTime(DEFAULT_REQUEST_TIMEOUT_MS * 2);

        expect(onError).not.toHaveBeenCalled();
      });
    });
  });

  describe('hai lệnh sửa', () => {
    const full: SubmitSellerProfileRequest = {
      sellerType: 'INDIVIDUAL',
      legalInfo: { legalName: 'Nguyễn A', identityNumber: '079123456789', businessAddress: '12 Lê Lợi, Quận 1, TP.HCM' },
      shop: { shopName: 'Shop A', contact: { personName: 'Nguyễn A', phone: '0901234567' } },
    };

    it('update: PUT tới /api/seller/seller/profile, thân là nguyên yêu cầu', () => {
      api.update(full).subscribe();
      const req = http.expectOne(SELLER_PROFILE_URL);

      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(full);
      req.flush(ok(null));
    });

    it('updateShopInfo: PATCH tới /api/seller/seller/profile/shop-info, giữ nguyên null để xóa', () => {
      const patch = { description: null, contact: { phone: '0912345678' } };
      api.updateShopInfo(patch).subscribe();
      const req = http.expectOne(SELLER_PROFILE_SHOP_INFO_URL);

      expect(req.request.method).toBe('PATCH');
      expect(req.request.url).toBe('/api/seller/seller/profile/shop-info');
      expect(req.request.body).toEqual(patch);
      req.flush(ok(null));
    });
  });
});
