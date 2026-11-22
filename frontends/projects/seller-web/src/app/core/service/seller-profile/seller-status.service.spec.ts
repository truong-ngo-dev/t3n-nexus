import {HttpTestingController, TestRequest, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideHttpClient} from '@angular/common/http';
import {TestBed} from '@angular/core/testing';
import {SELLER_PROFILE_URL} from './seller-profile.api';
import {SellerStatusService} from './seller-status.service';

/** Phản hồi 200 đúng khuôn `ApiResponse` với phần `data` do ca test chọn. */
function ok(data: unknown): object {
  return { code: 'OK', data, message: null, errors: null, traceId: 't-1' };
}

/**
 * Kiểm cách suy trạng thái từ phản hồi. Phản hồi sai khuôn từng kiểu (status lạ, thiếu data, thân là chuỗi) thuộc spec của
 * `SellerProfileApi`; ở đây chỉ cần một ca để chắc `InvalidApiResponse` thành `unknown`.
 */
describe('SellerStatusService (trạng thái hồ sơ người bán)', () => {
  let service: SellerStatusService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(SellerStatusService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /** Làm mới rồi để ca test trả lời yêu cầu đã gửi. */
  function refreshWith(respond: (req: TestRequest) => void): void {
    service.refresh().subscribe();
    respond(http.expectOne(SELLER_PROFILE_URL));
  }

  const notFound = (req: TestRequest) =>
    req.flush({ code: 'SELLER_PROFILE_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });

  it('bắt đầu ở trạng thái đang kiểm, chưa gọi mạng', () => {
    expect(service.state()).toEqual({ kind: 'loading' });
    http.expectNone(SELLER_PROFILE_URL);
  });

  describe('đọc được hồ sơ', () => {
    it('PENDING_REVIEW thành pending', () => {
      refreshWith((req) => req.flush(ok({ status: 'PENDING_REVIEW' })));
      expect(service.state()).toEqual({ kind: 'pending' });
    });

    it('APPROVED thành approved', () => {
      refreshWith((req) => req.flush(ok({ status: 'APPROVED' })));
      expect(service.state()).toEqual({ kind: 'approved' });
    });

    it('REJECTED kèm rejectionReason thành rejected và giữ lý do', () => {
      refreshWith((req) => req.flush(ok({ status: 'REJECTED', rejectionReason: 'Giấy tờ mờ' })));
      expect(service.state()).toEqual({ kind: 'rejected', reason: 'Giấy tờ mờ' });
    });

    it.each([[undefined], [null], [42], [{}]])('REJECTED với rejectionReason %j không phải chuỗi: vẫn rejected, lý do null', (reason) => {
      refreshWith((req) => req.flush(ok({ status: 'REJECTED', rejectionReason: reason })));
      expect(service.state()).toEqual({ kind: 'rejected', reason: null });
    });
  });

  describe('lỗi (R2, R3)', () => {
    it('404 kèm SELLER_PROFILE_NOT_FOUND thành none (chưa là người bán)', () => {
      refreshWith(notFound);
      expect(service.state()).toEqual({ kind: 'none' });
    });

    it.each([
      ['404 mã khác', (req: TestRequest) => req.flush({ code: 'SOMETHING_ELSE' }, { status: 404, statusText: 'Not Found' })],
      ['404 thân rỗng', (req: TestRequest) => req.flush(null, { status: 404, statusText: 'Not Found' })],
      ['500', (req: TestRequest) => req.flush(null, { status: 500, statusText: 'Error' })],
      ['lỗi mạng', (req: TestRequest) => req.error(new ProgressEvent('error'))],
      ['200 sai khuôn', (req: TestRequest) => req.flush(ok({ status: 'SUSPENDED' }))],
    ])('%s thành unknown, không phải none', (_name, respond) => {
      refreshWith(respond);
      expect(service.state()).toEqual({ kind: 'unknown' });
    });
  });

  describe('refresh()', () => {
    it.each([
      ['đọc được', (req: TestRequest) => req.flush(ok({ status: 'APPROVED' }))],
      ['lỗi', (req: TestRequest) => req.flush(null, { status: 500, statusText: 'Error' })],
    ])('%s: hoàn tất bình thường, không phát lỗi', (_name, respond) => {
      const onError = jest.fn();
      const onComplete = jest.fn();
      service.refresh().subscribe({ error: onError, complete: onComplete });
      respond(http.expectOne(SELLER_PROFILE_URL));
      expect(onError).not.toHaveBeenCalled();
      expect(onComplete).toHaveBeenCalledTimes(1);
    });

    it('lần sau thay kết quả lần trước; đang chờ thì giữ nguyên', () => {
      refreshWith((req) => req.flush(ok({ status: 'APPROVED' })));
      service.refresh().subscribe();
      expect(service.state()).toEqual({ kind: 'approved' });

      http.expectOne(SELLER_PROFILE_URL).flush(null, { status: 500, statusText: 'Error' });
      expect(service.state()).toEqual({ kind: 'unknown' });
    });
  });

  describe('loadProfile(): một lời gọi vừa trả hồ sơ vừa làm mới trạng thái', () => {
    it('trả đủ hồ sơ và cập nhật state', () => {
      const full = { id: 'p-1', status: 'APPROVED', sellerType: 'BUSINESS', legalInfo: {}, shop: { shopName: 'Shop A' } };
      let received: unknown;
      service.loadProfile().subscribe((profile) => (received = profile));
      http.expectOne(SELLER_PROFILE_URL).flush(ok(full));

      expect(received).toEqual(full);
      expect(service.state()).toEqual({ kind: 'approved' });
    });

    it('phát lỗi cho trang, state đã đặt đúng', () => {
      const onError = jest.fn();
      service.loadProfile().subscribe({ error: onError });
      notFound(http.expectOne(SELLER_PROFILE_URL));

      expect(onError).toHaveBeenCalledTimes(1);
      expect(service.state()).toEqual({ kind: 'none' });
    });
  });
});
