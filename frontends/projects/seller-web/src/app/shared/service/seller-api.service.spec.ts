import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {HttpErrorResponse, provideHttpClient} from '@angular/common/http';
import {TimeoutError} from 'rxjs';
import {DEFAULT_REQUEST_TIMEOUT_MS, InvalidApiResponse, classifyFailure} from '@t3n/http';
import {TestBed} from '@angular/core/testing';
import {SubmitSellerProfileRequest} from '../model/seller';
import {SELLER_PROFILE_RESUBMIT_URL, SELLER_PROFILE_URL, SellerApiService} from './seller-api.service';

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
    ['status lạ', ok({ status: 'SUSPENDED' }), 'data-mismatch'],
    ['thiếu status', ok({}), 'data-mismatch'],
    ['data là null', ok(null), 'data-mismatch'],
    ['success là false', { success: false, data: { status: 'APPROVED' } }, 'success-false'],
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

  /**
   * Nộp (`POST /profile`, 201) và nộp lại (`POST /profile/resubmit`, 200) cùng một khuôn yêu cầu. Thân phản hồi **không được dùng**
   * (sau khi nộp, trạng thái được tải lại bằng GET), nên dịch vụ chỉ báo thành công hoặc lỗi: `Observable<void>`. Vẫn kiểm thân có phải khung
   * thành công không (`expectSuccess`), để "2xx mà success:false" không lọt thành công; và có hạn chờ (`requestTimeout`).
   */
  describe.each([
    { name: 'submitSellerProfile', url: SELLER_PROFILE_URL, expectedUrl: '/api/seller/seller/profile', successStatus: 201 },
    { name: 'resubmitSellerProfile', url: SELLER_PROFILE_RESUBMIT_URL, expectedUrl: '/api/seller/seller/profile/resubmit', successStatus: 200 },
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
      http.expectOne(url).flush({ success: true }, { status: successStatus, statusText: 'OK' });

      expect(onError).not.toHaveBeenCalled();
      expect(onComplete).toHaveBeenCalledTimes(1);
    });

    it.each([
      ['thân null', null, 'not-envelope'],
      ['thân là chuỗi (trang HTML)', '<html>Đăng nhập</html>', 'not-envelope'],
      ['thân là đối tượng không có success', { data: { status: 'PENDING_REVIEW' } }, 'not-envelope'],
      ['success là false (2xx mà failed)', { success: false, code: 'SHOP_NAME_TAKEN', message: 'trùng', errors: [] }, 'success-false'],
    ])('2xx nhưng thân không đáng tin (%s): phát InvalidApiResponse lý do %s', (_name, body, reason) => {
      const onError = jest.fn();
      call().subscribe({ error: onError });
      http.expectOne(url).flush(body, { status: successStatus, statusText: 'OK' });

      expect(onError).toHaveBeenCalledTimes(1);
      expect(onError.mock.calls[0][0]).toBeInstanceOf(InvalidApiResponse);
      expect(onError.mock.calls[0][0].reason).toBe(reason);
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
});
