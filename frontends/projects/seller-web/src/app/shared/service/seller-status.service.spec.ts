import {HttpTestingController, TestRequest, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideHttpClient} from '@angular/common/http';
import {TestBed} from '@angular/core/testing';
import {SELLER_PROFILE_URL} from './seller-api.service';
import {SellerStatusService} from './seller-status.service';

/** Phản hồi 200 đúng khuôn `ApiResponse` với phần `data` do ca test chọn. */
function ok(data: unknown): object {
  return { success: true, data, message: null, errors: null, traceId: 't-1' };
}

describe('SellerStatusService (trạng thái hồ sơ người bán)', () => {
  let service: SellerStatusService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(SellerStatusService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /**
   * Gọi lấy hồ sơ rồi để ca test trả lời yêu cầu đã gửi.
   * */
  function load(respond: (req: TestRequest) => void): void {
    service.refresh().subscribe();
    respond(http.expectOne(SELLER_PROFILE_URL));
  }

  describe('khởi đầu và yêu cầu gửi đi', () => {
    it('bắt đầu ở trạng thái đang kiểm, chưa gọi mạng', () => {
      expect(service.state()).toEqual({ kind: 'loading' });
      http.expectNone(SELLER_PROFILE_URL);
    });

    it('lấy hồ sơ bằng GET tới đúng địa chỉ', () => {
      load((req) => {
        expect(req.request.method).toBe('GET');
        expect(req.request.url).toBe('/api/seller/seller/profile');
        req.flush(ok({ status: 'PENDING_REVIEW' }));
      });
    });
  });

  describe('200 hợp lệ', () => {
    it('PENDING_REVIEW thành pending', () => {
      load((req) => req.flush(ok({ status: 'PENDING_REVIEW' })));
      expect(service.state()).toEqual({ kind: 'pending' });
    });

    it('APPROVED thành approved', () => {
      load((req) => req.flush(ok({ status: 'APPROVED' })));
      expect(service.state()).toEqual({ kind: 'approved' });
    });

    it('REJECTED kèm rejectionReason thành rejected và giữ lý do', () => {
      load((req) => req.flush(ok({ status: 'REJECTED', rejectionReason: 'Giấy tờ mờ' })));
      expect(service.state()).toEqual({ kind: 'rejected', reason: 'Giấy tờ mờ' });
    });

    it.each([[undefined], [null], [42], [{}]])(
      'REJECTED với rejectionReason %j không phải chuỗi: vẫn rejected, lý do null',
      (reason) => {
        load((req) => req.flush(ok({ status: 'REJECTED', rejectionReason: reason })));
        expect(service.state()).toEqual({ kind: 'rejected', reason: null });
      },
    );
  });

  describe('200 nhưng sai khuôn: không xác định, không đoán', () => {
    it.each([
      ['status lạ', ok({ status: 'SUSPENDED' })],
      ['status thiếu', ok({})],
      ['status không phải chuỗi', ok({ status: 1 })],
      ['success là false', { success: false, data: { status: 'APPROVED' } }],
      ['thiếu success', { data: { status: 'APPROVED' } }],
      ['thiếu data', { success: true }],
      ['data là null', ok(null)],
      ['data là chuỗi', ok('APPROVED')],
      ['data là mảng', ok([{ status: 'APPROVED' }])],
      ['thân là null', null],
      ['thân là mảng', [ok({ status: 'APPROVED' })]],
      ['thân là chuỗi', 'APPROVED'],
    ])('%s thành unknown', (_name, body) => {
      load((req) => req.flush(body));
      expect(service.state()).toEqual({ kind: 'unknown' });
    });
  });

  describe('404 (R3)', () => {
    it('404 kèm SELLER_PROFILE_NOT_FOUND thành none (chưa là người bán)', () => {
      load((req) =>
        req.flush(
          { success: false, data: null, code: 'SELLER_PROFILE_NOT_FOUND' },
          { status: 404, statusText: 'Not Found' },
        ),
      );
      expect(service.state()).toEqual({ kind: 'none' });
    });

    it.each([
      ['mã khác', { success: false, data: null, code: 'SOMETHING_ELSE' }],
      ['thân không có code', { success: false, data: null }],
      ['thân rỗng', null],
    ])('404 %s thành unknown, không phải none', (_name, body) => {
      load((req) => req.flush(body, { status: 404, statusText: 'Not Found' }));
      expect(service.state()).toEqual({ kind: 'unknown' });
    });
  });

  describe('lỗi khác thành unknown (R2)', () => {
    it.each([401, 403, 500, 502, 503, 504])('lỗi %i thành unknown', (status) => {
      load((req) => req.flush({ success: false, data: null }, { status, statusText: 'Error' }));
      expect(service.state()).toEqual({ kind: 'unknown' });
    });

    it('lỗi mạng thành unknown', () => {
      load((req) => req.error(new ProgressEvent('error')));
      expect(service.state()).toEqual({ kind: 'unknown' });
    });
  });

  describe('luồng không bao giờ lỗi (như probe())', () => {
    it.each([
      ['200 hợp lệ', (req: TestRequest) => req.flush(ok({ status: 'APPROVED' }))],
      ['404 chưa có hồ sơ', (req: TestRequest) => req.flush({ code: 'SELLER_PROFILE_NOT_FOUND' }, { status: 404, statusText: 'Not Found' })],
      ['500', (req: TestRequest) => req.flush(null, { status: 500, statusText: 'Error' })],
      ['lỗi mạng', (req: TestRequest) => req.error(new ProgressEvent('error'))],
    ])('%s: hoàn tất bình thường, không phát lỗi', (_name, respond) => {
      const onError = jest.fn();
      const onComplete = jest.fn();
      service.refresh().subscribe({ error: onError, complete: onComplete });
      respond(http.expectOne(SELLER_PROFILE_URL));
      expect(onError).not.toHaveBeenCalled();
      expect(onComplete).toHaveBeenCalledTimes(1);
    });
  });

  describe('gọi lại (11b, nút thử lại)', () => {
    it('lần sau thay kết quả lần trước: none rồi approved', () => {
      load((req) => req.flush({ code: 'SELLER_PROFILE_NOT_FOUND' }, { status: 404, statusText: 'Not Found' }));
      expect(service.state()).toEqual({ kind: 'none' });

      load((req) => req.flush(ok({ status: 'APPROVED' })));
      expect(service.state()).toEqual({ kind: 'approved' });
    });

    it('đã approved mà lần làm mới lỗi 500 thì thành unknown, không giữ kết quả cũ', () => {
      load((req) => req.flush(ok({ status: 'APPROVED' })));
      expect(service.state()).toEqual({ kind: 'approved' });

      load((req) => req.flush(null, { status: 500, statusText: 'Error' }));
      expect(service.state()).toEqual({ kind: 'unknown' });
    });

    it('đang chờ phản hồi của lần gọi lại thì state() là giữ như trước đó', () => {
      load((req) => req.flush(ok({status: 'APPROVED' })));
      expect(service.state()).toEqual({ kind: 'approved' });
      service.refresh().subscribe();
      expect(service.state()).toEqual({ kind: 'approved' });
      http.expectOne(SELLER_PROFILE_URL).flush(ok({ status: 'APPROVED' }));
    });
  });

  describe('hồ sơ đầy đủ để render', () => {
    const FULL = {
      id: 'p-1',
      status: 'APPROVED',
      sellerType: 'BUSINESS',
      legalInfo: { legalName: 'Công ty A', taxCode: '0123456789' },
      shop: { shopName: 'Shop A', description: null, contact: { personName: 'Nam', phone: '0912345678' } },
      rejectionReason: null,
      submittedAt: '2026-01-01T00:00:00Z',
    };

    it('loadProfile() trả đủ body hồ sơ (bóc khỏi lớp bọc) và cập nhật state từ chính phản hồi đó', () => {
      let received: unknown;
      service.loadProfileStatus().subscribe((profile) => (received = profile));
      http.expectOne(SELLER_PROFILE_URL).flush(ok(FULL));

      expect(received).toEqual(FULL);
      expect(service.state()).toEqual({ kind: 'approved' });
    });

    it('loadProfile() phát lỗi khi 200 sai khuôn (status lạ) và đặt state là unknown', () => {
      const onError = jest.fn();
      service.loadProfileStatus().subscribe({ error: onError });
      http.expectOne(SELLER_PROFILE_URL).flush(ok({ status: 'SUSPENDED' }));

      expect(onError).toHaveBeenCalledTimes(1);
      expect(service.state()).toEqual({ kind: 'unknown' });
    });

    it('loadProfile() phát lỗi khi 404 chưa có hồ sơ và đặt state là none', () => {
      const onError = jest.fn();
      service.loadProfileStatus().subscribe({ error: onError });
      http
        .expectOne(SELLER_PROFILE_URL)
        .flush({ code: 'SELLER_PROFILE_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });

      expect(onError).toHaveBeenCalledTimes(1);
      expect(service.state()).toEqual({ kind: 'none' });
    });

    it('lỗi 500 phát lỗi cho component và đặt state là unknown', () => {
      const onError = jest.fn();
      service.loadProfileStatus().subscribe({ error: onError });
      http.expectOne(SELLER_PROFILE_URL).flush(null, { status: 500, statusText: 'Error' });

      expect(onError).toHaveBeenCalledTimes(1);
      expect(service.state()).toEqual({ kind: 'unknown' });
    });
  });
});
