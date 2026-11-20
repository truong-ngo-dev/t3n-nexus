import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { NEVER, Subject, TimeoutError, lastValueFrom, of, throwError } from 'rxjs';
import { DEFAULT_REQUEST_TIMEOUT_MS, FailureClass, InvalidApiResponse, InvalidApiResponseReason, classifyFailure, expectNoBody, expectSuccess, expectSuccessEnvelope, requestTimeout, unwrapApiResponse, unwrapData } from '@t3n/http';

/** Thân thành công đúng khuôn `ApiResponse` (api-response.ts của `@t3n/model`). */
function ok(data: unknown): object {
  return { success: true, data, message: null, errors: null, traceId: 't-1' };
}

/** Hàm kiểm `data` điển hình: chỉ nhận đối tượng có `status` kiểu chuỗi, còn lại `null`. */
function parseStatus(data: unknown): { status: string } | null {
  return typeof data === 'object' && data !== null && typeof (data as Record<string, unknown>)['status'] === 'string'
    ? (data as { status: string })
    : null;
}

/** Chạy hàm, trả lý do của `InvalidApiResponse` nó ném ra; không ném hoặc ném lỗi khác thì làm ca test đỏ. */
function reasonOf(run: () => unknown): InvalidApiResponseReason {
  try {
    run();
  } catch (error) {
    expect(error).toBeInstanceOf(InvalidApiResponse);
    return (error as InvalidApiResponse).reason;
  }
  throw new Error('Mong đợi ném InvalidApiResponse nhưng hàm chạy xong');
}

describe('InvalidApiResponse', () => {
  it('là một Error, mang lý do và tên riêng để log và phân loại', () => {
    const error = new InvalidApiResponse('success-false');

    expect(error).toBeInstanceOf(Error);
    expect(error).toBeInstanceOf(InvalidApiResponse);
    expect(error.name).toBe('InvalidApiResponse');
    expect(error.reason).toBe('success-false');
    expect(error.message).toContain('success-false');
  });
});

describe('unwrapApiResponse (nhánh thành công: ba cửa)', () => {
  it('qua đủ ba cửa: trả đúng kết quả của hàm kiểm data', () => {
    const data = { status: 'PENDING_REVIEW', id: 'p-1' };

    expect(unwrapApiResponse(ok(data), parseStatus)).toBe(data);
  });

  it('gọi hàm kiểm đúng một lần và chỉ với phần data (không đưa cả khung vào)', () => {
    const parse = jest.fn(parseStatus);
    const data = { status: 'APPROVED' };

    unwrapApiResponse(ok(data), parse);

    expect(parse).toHaveBeenCalledTimes(1);
    expect(parse).toHaveBeenCalledWith(data);
  });

  describe('cửa 1: thân phải là một khung', () => {
    it.each([
      ['null', null],
      ['undefined', undefined],
      ['chuỗi (vd trang HTML trả 200)', '<html>Đăng nhập</html>'],
      ['số', 42],
      ['mảng', [{ success: true }]],
      ['đối tượng không có success', { data: { status: 'APPROVED' } }],
      ['success là chuỗi "true"', { success: 'true', data: { status: 'APPROVED' } }],
      ['success là số 1', { success: 1, data: { status: 'APPROVED' } }],
      ['success là null', { success: null, data: { status: 'APPROVED' } }],
    ])('%s: not-envelope', (_name, body) => {
      expect(reasonOf(() => unwrapApiResponse(body, parseStatus))).toBe('not-envelope');
    });
  });

  describe('cửa 2: success phải đúng là true', () => {
    it('success là false (200 nhưng failed): success-false', () => {
      const body = { success: false, code: 'SHOP_NAME_TAKEN', message: 'trùng', errors: [], data: { status: 'APPROVED' } };

      expect(reasonOf(() => unwrapApiResponse(body, parseStatus))).toBe('success-false');
    });

    it('success-false thắng cả khi data trông hợp lệ (không đọc data của một phản hồi đã báo thất bại)', () => {
      const parse = jest.fn(parseStatus);

      expect(reasonOf(() => unwrapApiResponse({ success: false, data: { status: 'APPROVED' } }, parse))).toBe('success-false');
      expect(parse).not.toHaveBeenCalled();
    });
  });

  describe('cửa 3: data phải đúng khuôn endpoint cần', () => {
    it.each([
      ['thiếu data', { success: true }],
      ['data là null', ok(null)],
      ['data không phải đối tượng', ok('abc')],
      ['data thiếu trường quyết định luồng', ok({ id: 'p-1' })],
      ['trường quyết định sai kiểu', ok({ status: 7 })],
    ])('%s: data-mismatch', (_name, body) => {
      expect(reasonOf(() => unwrapApiResponse(body, parseStatus))).toBe('data-mismatch');
    });

    it('hàm kiểm trả null thì là data-mismatch, kể cả khi data không rỗng', () => {
      expect(reasonOf(() => unwrapApiResponse(ok({ status: 'APPROVED' }), () => null))).toBe('data-mismatch');
    });
  });

  it('cửa 1 và 2 chạy trước cửa 3: thân hỏng không bao giờ làm hàm kiểm chạy', () => {
    const parse = jest.fn(parseStatus);

    reasonOf(() => unwrapApiResponse(null, parse));
    reasonOf(() => unwrapApiResponse({ success: false }, parse));

    expect(parse).not.toHaveBeenCalled();
  });

  it('lỗi do chính hàm kiểm ném ra không bị nuốt hay đổi thành data-mismatch (đó là lỗi lập trình)', () => {
    const boom = new Error('lỗi trong hàm kiểm');

    expect(() => unwrapApiResponse(ok({ status: 'APPROVED' }), () => { throw boom; })).toThrow(boom);
  });

  it('không sửa thân đưa vào', () => {
    const body = ok({ status: 'APPROVED' });
    const snapshot = JSON.stringify(body);

    unwrapApiResponse(body, parseStatus);

    expect(JSON.stringify(body)).toBe(snapshot);
  });
});

describe('expectSuccessEnvelope (endpoint không dùng data: chỉ cửa 1 và 2)', () => {
  it.each([
    ['có data', ok({ status: 'APPROVED' })],
    ['data là null', ok(null)],
    ['không có data', { success: true }],
  ])('thân thành công %s: không ném', (_name, body) => {
    expect(() => expectSuccessEnvelope(body)).not.toThrow();
  });

  it('không đọc data: data sai khuôn vẫn qua', () => {
    expect(() => expectSuccessEnvelope(ok({ khongPhaiStatus: true }))).not.toThrow();
  });

  it.each([
    ['null', null, 'not-envelope'],
    ['chuỗi', 'oops', 'not-envelope'],
    ['thiếu success', { data: 1 }, 'not-envelope'],
    ['success false', { success: false }, 'success-false'],
  ])('%s: %s', (_name, body, reason) => {
    expect(reasonOf(() => expectSuccessEnvelope(body))).toBe(reason);
  });
});

describe('toán tử RxJS', () => {
  describe('unwrapData', () => {
    it('phát đúng kết quả của hàm kiểm rồi hoàn tất', async () => {
      const data = { status: 'APPROVED' };

      await expect(lastValueFrom(of(ok(data)).pipe(unwrapData(parseStatus)))).resolves.toBe(data);
    });

    it('thân hỏng: phát InvalidApiResponse vào kênh lỗi, không phát giá trị nào', async () => {
      const onNext = jest.fn();
      const error = await lastValueFrom(of(ok({ status: 7 })).pipe(unwrapData(parseStatus))).then(
        (value) => { onNext(value); return null; },
        (e: unknown) => e,
      );

      expect(onNext).not.toHaveBeenCalled();
      expect(error).toBeInstanceOf(InvalidApiResponse);
      expect((error as InvalidApiResponse).reason).toBe('data-mismatch');
    });

    it('lỗi HTTP đi qua nguyên vẹn, không bị đổi kiểu (toErrorInfo còn đọc được)', async () => {
      const httpError = new HttpErrorResponse({ status: 409, error: { success: false, code: 'SHOP_NAME_TAKEN' } });

      const error = await lastValueFrom(throwError(() => httpError).pipe(unwrapData(parseStatus))).catch((e: unknown) => e);

      expect(error).toBe(httpError);
    });
  });

  describe('expectSuccess', () => {
    it('thân thành công: phát một giá trị void rồi hoàn tất', async () => {
      await expect(lastValueFrom(of(ok({ gi: 'do' })).pipe(expectSuccess()))).resolves.toBeUndefined();
    });

    it('success false: phát InvalidApiResponse success-false', async () => {
      const error = await lastValueFrom(of({ success: false }).pipe(expectSuccess())).catch((e: unknown) => e);

      expect(error).toBeInstanceOf(InvalidApiResponse);
      expect((error as InvalidApiResponse).reason).toBe('success-false');
    });

    it('lỗi HTTP đi qua nguyên vẹn', async () => {
      const httpError = new HttpErrorResponse({ status: 503 });

      const error = await lastValueFrom(throwError(() => httpError).pipe(expectSuccess())).catch((e: unknown) => e);

      expect(error).toBe(httpError);
    });
  });

  describe('expectNoBody (endpoint cố ý không có thân)', () => {
    it.each([
      ['thân rỗng (204)', null],
      ['khung thành công', ok({ gi: 'do' })],
      ['thân lạ vẫn không bị kiểm (chỉ HttpClient mới kiểm được)', '<html></html>'],
    ])('%s: phát một giá trị void rồi hoàn tất', async (_name, body) => {
      await expect(lastValueFrom(of(body).pipe(expectNoBody()))).resolves.toBeUndefined();
    });

    it('lỗi HTTP đi qua nguyên vẹn', async () => {
      const httpError = new HttpErrorResponse({ status: 503 });

      const error = await lastValueFrom(throwError(() => httpError).pipe(expectNoBody())).catch((e: unknown) => e);

      expect(error).toBe(httpError);
    });
  });
});

function http(status: number, body: unknown = null, headers: Record<string, string> = {}): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: body, headers: new HttpHeaders(headers) });
}

function failure(code: string): object {
  return { success: false, code, message: 'Thông điệp của máy chủ', errors: [] };
}

describe('classifyFailure: phân loại theo bản chất lỗi, không theo tính năng', () => {
  describe('handled: bộ chặn chung đã lo, nơi gọi không làm gì', () => {
    it.each([
      ['401 có khung lỗi', http(401, failure('UNAUTHENTICATED'))],
      ['401 thân rỗng (bảo mật của seller-service)', http(401)],
    ])('%s', (_name, error) => {
      expect(classifyFailure(error)).toBe<FailureClass>('handled');
    });
  });

  describe('transient: chưa ghi gì, an toàn để cho gửi lại', () => {
    it.each([
      ['mất mạng (status 0)', http(0)],
      ['429 quá nhiều yêu cầu', http(429, failure('TOO_MANY_REQUESTS'), { 'Retry-After': '30' })],
      ['429 thiếu Retry-After', http(429)],
      ['502 cổng xấu', http(502)],
      ['503 dịch vụ không với tới', http(503, failure('SERVICE_UNAVAILABLE'))],
      ['500 lỗi máy chủ có khung', http(500, failure('INTERNAL_ERROR'))],
      ['500 thân không phải JSON', http(500, 'oops')],
      ['403 CSRF vẫn sai sau lần thử lại của bộ chặn', http(403, failure('CSRF_TOKEN_INVALID'))],
    ])('%s', (_name, error) => {
      expect(classifyFailure(error)).toBe<FailureClass>('transient');
    });
  });

  describe('unknown-outcome: có thể ĐÃ ghi rồi, phải kiểm lại trạng thái trước khi gửi lại', () => {
    it('504: cổng hết giờ chờ, dịch vụ phía sau có thể vẫn xử lý xong', () => {
      expect(classifyFailure(http(504))).toBe<FailureClass>('unknown-outcome');
    });

    it('hết giờ phía giao diện (TimeoutError của RxJS)', () => {
      expect(classifyFailure(new TimeoutError())).toBe<FailureClass>('unknown-outcome');
    });

    it('InvalidApiResponse: 2xx nhưng thân không đáng tin, thao tác có thể đã được ghi', () => {
      expect(classifyFailure(new InvalidApiResponse('not-envelope'))).toBe<FailureClass>('unknown-outcome');
      expect(classifyFailure(new InvalidApiResponse('success-false'))).toBe<FailureClass>('unknown-outcome');
      expect(classifyFailure(new InvalidApiResponse('data-mismatch'))).toBe<FailureClass>('unknown-outcome');
    });

    it('Angular báo HttpErrorResponse mang trạng thái 2xx khi không đọc được thân (JSON hỏng): kết quả chưa rõ', () => {
      expect(classifyFailure(http(200, 'không phải JSON'))).toBe<FailureClass>('unknown-outcome');
      expect(classifyFailure(http(201))).toBe<FailureClass>('unknown-outcome');
    });
  });

  describe('rejected: máy chủ từ chối có chủ đích (4xx), tính năng tự quyết gắn vào ô hay câu chung', () => {
    it.each([
      ['400 lỗi trường', http(400, { ...failure('SELLER_FIELD_INVALID'), errors: ['shopName'] })],
      ['400 không có khung', http(400)],
      ['404 không có hồ sơ', http(404, failure('SELLER_PROFILE_NOT_FOUND'))],
      ['409 trùng tên', http(409, failure('SHOP_NAME_TAKEN'))],
      ['409 trạng thái đã đổi', http(409, failure('INVALID_STATE'))],
      ['422 mã lạ', http(422, failure('MA_LA'))],
      ['403 tài khoản quản trị', http(403, failure('SELLER_ROLE_NOT_ALLOWED'))],
      ['403 FORBIDDEN của BFF', http(403, failure('FORBIDDEN'))],
      ['403 thân rỗng (denyAll của seller-service)', http(403)],
    ])('%s', (_name, error) => {
      expect(classifyFailure(error)).toBe<FailureClass>('rejected');
    });
  });

  describe('unexpected: lưới đỡ', () => {
    it.each([
      ['Error thường (lỗi lập trình)', new Error('boom')],
      ['chuỗi', 'oops'],
      ['undefined', undefined],
      ['null', null],
      ['3xx (chuyển hướng không được theo dõi)', http(302)],
    ])('%s', (_name, error) => {
      expect(classifyFailure(error)).toBe<FailureClass>('unexpected');
    });
  });

  it('không đọc chữ thông điệp của máy chủ để quyết định (R6): cùng status và code thì cùng loại bất kể message', () => {
    const a = http(409, { code: 'SHOP_NAME_TAKEN', message: 'trùng tên rồi' });
    const b = http(409, { code: 'SHOP_NAME_TAKEN', message: 'lời nhắn hoàn toàn khác, nhắc tới timeout và 504' });

    expect(classifyFailure(a)).toBe(classifyFailure(b));
  });

  it('không ném với đầu vào lạ', () => {
    for (const input of [{}, [], 0, NaN, Symbol('x'), () => 1]) {
      expect(() => classifyFailure(input)).not.toThrow();
    }
  });
});

describe('requestTimeout', () => {
  beforeEach(() => jest.useFakeTimers());
  afterEach(() => jest.useRealTimers());

  it('mặc định là 30 giây, dài hơn response-timeout 25 giây của BFF để 504 của BFF tới trước', () => {
    expect(DEFAULT_REQUEST_TIMEOUT_MS).toBe(30_000);
  });

  it('có giá trị trước hạn thì cho qua nguyên vẹn và hoàn tất bình thường', async () => {
    const value = { ok: true };

    await expect(lastValueFrom(of(value).pipe(requestTimeout(1000)))).resolves.toBe(value);
  });

  it('quá hạn mà chưa có giá trị: phát TimeoutError vào kênh lỗi', () => {
    const onError = jest.fn();
    NEVER.pipe(requestTimeout(1000)).subscribe({ error: onError });

    jest.advanceTimersByTime(999);
    expect(onError).not.toHaveBeenCalled();

    jest.advanceTimersByTime(1);
    expect(onError).toHaveBeenCalledTimes(1);
    expect(onError.mock.calls[0][0]).toBeInstanceOf(TimeoutError);
  });

  it('dùng hạn mặc định khi không truyền tham số', () => {
    const onError = jest.fn();
    NEVER.pipe(requestTimeout()).subscribe({ error: onError });

    jest.advanceTimersByTime(DEFAULT_REQUEST_TIMEOUT_MS - 1);
    expect(onError).not.toHaveBeenCalled();

    jest.advanceTimersByTime(1);
    expect(onError).toHaveBeenCalledTimes(1);
  });

  it('giá trị tới sát hạn thì thắng, không phát lỗi sau đó', () => {
    const source = new Subject<string>();
    const onNext = jest.fn();
    const onError = jest.fn();
    source.pipe(requestTimeout(1000)).subscribe({ next: onNext, error: onError });

    jest.advanceTimersByTime(999);
    source.next('đến kịp');
    source.complete();
    jest.advanceTimersByTime(5000);

    expect(onNext).toHaveBeenCalledWith('đến kịp');
    expect(onError).not.toHaveBeenCalled();
  });

  it('lỗi của nguồn đi qua nguyên vẹn, không bị đổi thành TimeoutError', () => {
    const httpError = http(503);
    const source = new Subject<string>();
    const onError = jest.fn();
    source.pipe(requestTimeout(1000)).subscribe({ error: onError });

    source.error(httpError);

    expect(onError).toHaveBeenCalledWith(httpError);
  });

  it('TimeoutError do requestTimeout phát ra được classifyFailure xếp đúng loại unknown-outcome', () => {
    const onError = jest.fn();
    NEVER.pipe(requestTimeout(10)).subscribe({ error: onError });

    jest.advanceTimersByTime(10);

    expect(classifyFailure(onError.mock.calls[0][0])).toBe<FailureClass>('unknown-outcome');
  });
});
