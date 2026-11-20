import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { TimeoutError } from 'rxjs';
import { SellerProfile, SellerType } from '../../shared/model/seller';
import { InvalidApiResponse } from '@t3n/http';
import {
  ProfileFormField,
  ProfileFormValue,
  SubmitErrorView,
  buildSubmitRequest,
  emptyProfileForm,
  fieldLabel,
  formValueFromProfile,
  isFieldRequired,
  legalFieldsFor,
  mapSubmitError,
  validateProfileForm,
} from './profile-form';

/** Biểu mẫu hợp lệ cho loại hình đã chọn; ca test ghi đè đúng trường cần thử. */
function valid(sellerType: SellerType, override: Partial<ProfileFormValue> = {}): ProfileFormValue {
  return {
    sellerType,
    legalName: 'Nguyễn Văn A',
    representativeName: sellerType === 'BUSINESS' ? 'Trần Thị B' : '',
    identityNumber: '079123456789',
    taxCode: sellerType === 'INDIVIDUAL' ? '' : '0123456789',
    businessAddress: '12 Lê Lợi, Quận 1, TP.HCM',
    shopName: 'Cửa hàng A',
    description: '',
    contactPersonName: 'Nguyễn Văn A',
    contactPhone: '0901234567',
    contactAddress: '',
    ...override,
  };
}

const TYPES: SellerType[] = ['INDIVIDUAL', 'HOUSEHOLD', 'BUSINESS'];

describe('emptyProfileForm', () => {
  it('chưa chọn loại hình, mọi ô là chuỗi rỗng', () => {
    const form = emptyProfileForm();

    expect(form.sellerType).toBeNull();
    for (const [key, value] of Object.entries(form)) {
      if (key !== 'sellerType') expect(value).toBe('');
    }
  });

  it('mỗi lần gọi trả một đối tượng mới (hai biểu mẫu không dùng chung)', () => {
    expect(emptyProfileForm()).not.toBe(emptyProfileForm());
  });
});

describe('legalFieldsFor (ô pháp lý hiện theo loại hình, KT-07)', () => {
  it('cá nhân và hộ kinh doanh: không có người đại diện', () => {
    const expected: ProfileFormField[] = ['legalName', 'identityNumber', 'taxCode', 'businessAddress'];
    expect(legalFieldsFor('INDIVIDUAL')).toEqual(expected);
    expect(legalFieldsFor('HOUSEHOLD')).toEqual(expected);
  });

  it('doanh nghiệp: thêm người đại diện, ngay sau tên pháp lý', () => {
    expect(legalFieldsFor('BUSINESS')).toEqual(['legalName', 'representativeName', 'identityNumber', 'taxCode', 'businessAddress']);
  });

  it('chưa chọn loại hình: chưa hiện ô pháp lý nào', () => {
    expect(legalFieldsFor(null)).toEqual([]);
  });
});

describe('isFieldRequired (dấu * và nhãn "tùy chọn", KT-07)', () => {
  it.each<[SellerType, ProfileFormField, boolean]>([
    ['INDIVIDUAL', 'taxCode', false],
    ['HOUSEHOLD', 'taxCode', true],
    ['BUSINESS', 'taxCode', true],
    ['BUSINESS', 'representativeName', true],
    ['INDIVIDUAL', 'legalName', true],
    ['INDIVIDUAL', 'identityNumber', true],
    ['INDIVIDUAL', 'businessAddress', true],
    ['INDIVIDUAL', 'shopName', true],
    ['INDIVIDUAL', 'contactPersonName', true],
    ['INDIVIDUAL', 'contactPhone', true],
    ['INDIVIDUAL', 'description', false],
    ['INDIVIDUAL', 'contactAddress', false],
  ])('%s, %s: bắt buộc = %s', (type, field, expected) => {
    expect(isFieldRequired(type, field)).toBe(expected);
  });
});

const ALL_FIELDS: ProfileFormField[] = [
  'sellerType', 'legalName', 'representativeName', 'identityNumber', 'taxCode', 'businessAddress',
  'shopName', 'description', 'contactPersonName', 'contactPhone', 'contactAddress',
];

describe('fieldLabel (nhãn ô, KT-07)', () => {
  it.each<[SellerType, string, string]>([
    ['INDIVIDUAL', 'Họ tên', 'Địa chỉ kinh doanh'],
    ['HOUSEHOLD', 'Tên chủ hộ', 'Địa chỉ kinh doanh'],
    ['BUSINESS', 'Tên doanh nghiệp', 'Địa chỉ trụ sở'],
  ])('%s: tên pháp lý là "%s", địa chỉ là "%s"', (type, legalName, businessAddress) => {
    expect(fieldLabel(type, 'legalName')).toBe(legalName);
    expect(fieldLabel(type, 'businessAddress')).toBe(businessAddress);
  });

  it.each(TYPES)('%s: mọi ô có nhãn không rỗng, không kèm dấu * hay chữ "(tùy chọn)" (mẫu tự thêm theo isFieldRequired)', (type) => {
    for (const field of ALL_FIELDS) {
      const label = fieldLabel(type, field);
      expect(label.trim().length).toBeGreaterThan(0);
      expect(label).not.toContain('*');
      expect(label).not.toContain('tùy chọn');
    }
  });

  it('nhãn các ô khác nhau không trùng nhau trong cùng một loại hình', () => {
    const labels = ALL_FIELDS.map((field) => fieldLabel('BUSINESS', field));

    expect(new Set(labels).size).toBe(labels.length);
  });

  it('chưa chọn loại hình: vẫn trả nhãn không rỗng, không ném (ô pháp lý chưa hiện nhưng hàm phải dùng được)', () => {
    for (const field of ALL_FIELDS) {
      expect(fieldLabel(null, field).trim().length).toBeGreaterThan(0);
    }
  });
});

describe('validateProfileForm (KT-07)', () => {
  it.each(TYPES)('biểu mẫu hợp lệ cho %s: không có lỗi nào', (type) => {
    expect(validateProfileForm(valid(type))).toStrictEqual({});
  });

  it('chưa chọn loại hình: lỗi ở loại hình, không bắt lỗi ô pháp lý (chưa hiện)', () => {
    const errors = validateProfileForm({ ...emptyProfileForm(), shopName: 'Cửa hàng A', contactPersonName: 'A B', contactPhone: '0901234567' });

    expect(errors.sellerType).toBe('required');
    expect(errors.legalName).toBeUndefined();
    expect(errors.identityNumber).toBeUndefined();
  });

  it('biểu mẫu trống hoàn toàn: báo thiếu đúng các ô bắt buộc, không báo ô tùy chọn', () => {
    expect(validateProfileForm(emptyProfileForm())).toStrictEqual({
      sellerType: 'required',
      shopName: 'required',
      contactPersonName: 'required',
      contactPhone: 'required',
    });
  });

  describe('thiếu ô bắt buộc theo loại hình', () => {
    it.each([
      ['INDIVIDUAL', 'legalName'],
      ['INDIVIDUAL', 'identityNumber'],
      ['INDIVIDUAL', 'businessAddress'],
      ['HOUSEHOLD', 'taxCode'],
      ['BUSINESS', 'taxCode'],
      ['BUSINESS', 'representativeName'],
      ['BUSINESS', 'legalName'],
      ['INDIVIDUAL', 'shopName'],
      ['INDIVIDUAL', 'contactPersonName'],
      ['INDIVIDUAL', 'contactPhone'],
    ] as const)('%s bỏ trống %s: required', (type, field) => {
      expect(validateProfileForm(valid(type, { [field]: '' }))).toStrictEqual({ [field]: 'required' });
    });

    it('toàn khoảng trắng coi như bỏ trống', () => {
      expect(validateProfileForm(valid('INDIVIDUAL', { legalName: '   ' }))).toStrictEqual({ legalName: 'required' });
    });

    it('cá nhân bỏ trống mã số thuế: hợp lệ (tùy chọn)', () => {
      expect(validateProfileForm(valid('INDIVIDUAL', { taxCode: '' }))).toStrictEqual({});
    });
  });

  describe('ô không hiện theo loại hình thì không bị kiểm (kể cả khi còn giá trị rác từ lần chọn trước)', () => {
    it.each(['INDIVIDUAL', 'HOUSEHOLD'] as const)('%s có representativeName sai: bỏ qua', (type) => {
      expect(validateProfileForm(valid(type, { representativeName: 'x' }))).toStrictEqual({});
    });
  });

  describe('định dạng', () => {
    it.each([
      ['079123456789', true],
      [' 079123456789 ', true],
      ['07912345678', false],
      ['0791234567890', false],
      ['07912345678a', false],
      ['079 123 456 789', true], // máy chủ bỏ mọi khoảng trắng trước khi kiểm
    ])('số căn cước %j hợp lệ = %s', (identityNumber, ok) => {
      const errors = validateProfileForm(valid('INDIVIDUAL', { identityNumber }));
      expect(errors.identityNumber).toBe(ok ? undefined : 'invalid');
    });

    it.each([
      ['0123456789', true],
      ['0123456789012', true],
      ['0123456789-001', true],
      ['0123 456 789', true], // máy chủ bỏ mọi khoảng trắng trước khi kiểm
      ['012345678', false],
      ['01234567890', false],
      ['0123456789-01', false],
      ['0123456789001x', false],
    ])('mã số thuế %j hợp lệ = %s', (taxCode, ok) => {
      const errors = validateProfileForm(valid('HOUSEHOLD', { taxCode }));
      expect(errors.taxCode).toBe(ok ? undefined : 'invalid');
    });

    it('cá nhân có điền mã số thuế thì phải đúng định dạng', () => {
      expect(validateProfileForm(valid('INDIVIDUAL', { taxCode: '123' })).taxCode).toBe('invalid');
      expect(validateProfileForm(valid('INDIVIDUAL', { taxCode: '0123456789' })).taxCode).toBeUndefined();
    });

    it.each([
      ['0901234567', true],
      ['0351234567', true],
      ['+84 901 234 567', true],
      ['84901234567', true],
      ['090.123.4567', true],
      ['090-123-4567', true],
      ['(090) 123 4567', true],
      ['0201234567', false],
      ['090123456', false],
      ['09012345678', false],
      ['8490123456', false],
      ['abc', false],
    ])('số điện thoại %j hợp lệ = %s', (contactPhone, ok) => {
      const errors = validateProfileForm(valid('INDIVIDUAL', { contactPhone }));
      expect(errors.contactPhone).toBe(ok ? undefined : 'invalid');
    });
  });

  describe('độ dài (sau khi bỏ khoảng trắng hai đầu và gộp khoảng trắng liền nhau, như máy chủ)', () => {
    it.each<[ProfileFormField, number, number]>([
      ['legalName', 2, 150],
      ['shopName', 3, 60],
      ['contactPersonName', 2, 100],
      ['businessAddress', 10, 300],
    ])('%s: nhận từ %i đến %i ký tự', (field, min, max) => {
      const at = (n: number) => validateProfileForm(valid('INDIVIDUAL', { [field]: 'a'.repeat(n) }))[field];

      expect(at(min - 1)).toBe('invalid');
      expect(at(min)).toBeUndefined();
      expect(at(max)).toBeUndefined();
      expect(at(max + 1)).toBe('invalid');
    });

    it('người đại diện (doanh nghiệp): 2 đến 100 ký tự', () => {
      const at = (n: number) => validateProfileForm(valid('BUSINESS', { representativeName: 'a'.repeat(n) })).representativeName;

      expect(at(1)).toBe('invalid');
      expect(at(2)).toBeUndefined();
      expect(at(100)).toBeUndefined();
      expect(at(101)).toBe('invalid');
    });

    it('mô tả tùy chọn: tới 500 ký tự', () => {
      const at = (n: number) => validateProfileForm(valid('INDIVIDUAL', { description: 'a'.repeat(n) })).description;

      expect(at(0)).toBeUndefined();
      expect(at(500)).toBeUndefined();
      expect(at(501)).toBe('invalid');
    });

    it('địa chỉ liên hệ tùy chọn: bỏ trống được, có điền thì 10 đến 300 ký tự', () => {
      const at = (n: number) => validateProfileForm(valid('INDIVIDUAL', { contactAddress: 'a'.repeat(n) })).contactAddress;

      expect(at(0)).toBeUndefined();
      expect(at(9)).toBe('invalid');
      expect(at(10)).toBeUndefined();
      expect(at(301)).toBe('invalid');
    });

    it('đếm sau khi bỏ khoảng trắng hai đầu và gộp khoảng trắng liền nhau', () => {
      expect(validateProfileForm(valid('INDIVIDUAL', { shopName: ' A     B ' })).shopName).toBeUndefined(); // "A B" = 3
      expect(validateProfileForm(valid('INDIVIDUAL', { shopName: '  A   ' })).shopName).toBe('invalid'); // "A" = 1
    });
  });

  it('nhiều lỗi cùng lúc: báo đủ từng ô, không dừng ở lỗi đầu', () => {
    const errors = validateProfileForm(valid('BUSINESS', { identityNumber: '1', taxCode: '', shopName: 'ab', contactPhone: '123' }));

    expect(errors).toStrictEqual({ identityNumber: 'invalid', taxCode: 'required', shopName: 'invalid', contactPhone: 'invalid' });
  });

  it('không sửa biểu mẫu đưa vào', () => {
    const form = valid('INDIVIDUAL', { legalName: '  Nguyễn   A  ' });
    const snapshot = { ...form };

    validateProfileForm(form);

    expect(form).toEqual(snapshot);
  });
});

describe('buildSubmitRequest (KT-08)', () => {
  it('doanh nghiệp đủ trường: dựng đúng khuôn yêu cầu của api.yaml', () => {
    const form = valid('BUSINESS', {
      description: 'Bán đồ gia dụng',
      contactAddress: '34 Nguyễn Huệ, Quận 1, TP.HCM',
    });

    expect(buildSubmitRequest(form)).toStrictEqual({
      sellerType: 'BUSINESS',
      legalInfo: {
        legalName: 'Nguyễn Văn A',
        representativeName: 'Trần Thị B',
        identityNumber: '079123456789',
        taxCode: '0123456789',
        businessAddress: '12 Lê Lợi, Quận 1, TP.HCM',
      },
      shop: {
        shopName: 'Cửa hàng A',
        description: 'Bán đồ gia dụng',
        contact: { personName: 'Nguyễn Văn A', phone: '0901234567', address: '34 Nguyễn Huệ, Quận 1, TP.HCM' },
      },
    });
  });

  it('ô tùy chọn để trống thì vắng hẳn khỏi yêu cầu (không gửi chuỗi rỗng)', () => {
    const request = buildSubmitRequest(valid('INDIVIDUAL'));

    expect(request.legalInfo).toStrictEqual({
      legalName: 'Nguyễn Văn A',
      identityNumber: '079123456789',
      businessAddress: '12 Lê Lợi, Quận 1, TP.HCM',
    });
    expect(request.shop).toStrictEqual({
      shopName: 'Cửa hàng A',
      contact: { personName: 'Nguyễn Văn A', phone: '0901234567' },
    });
  });

  it('cá nhân có điền mã số thuế: gửi kèm', () => {
    expect(buildSubmitRequest(valid('INDIVIDUAL', { taxCode: '0123456789' })).legalInfo.taxCode).toBe('0123456789');
  });

  it.each(['INDIVIDUAL', 'HOUSEHOLD'] as const)('%s: không gửi người đại diện dù ô còn giá trị rác', (type) => {
    const request = buildSubmitRequest(valid(type, { representativeName: 'Trần Thị B' }));

    expect(request.legalInfo).not.toHaveProperty('representativeName');
  });

  it('bỏ khoảng trắng hai đầu mọi ô, giữ nguyên khoảng trắng bên trong (máy chủ tự chuẩn hóa)', () => {
    const request = buildSubmitRequest(valid('INDIVIDUAL', { legalName: '  Nguyễn   Văn A  ', shopName: ' Cửa hàng A ', contactPhone: ' 0901234567 ' }));

    expect(request.legalInfo.legalName).toBe('Nguyễn   Văn A');
    expect(request.shop.shopName).toBe('Cửa hàng A');
    expect(request.shop.contact?.phone).toBe('0901234567');
  });

  it('ô tùy chọn toàn khoảng trắng coi như để trống', () => {
    const request = buildSubmitRequest(valid('INDIVIDUAL', { description: '   ', contactAddress: '  ' }));

    expect(request.shop).not.toHaveProperty('description');
    expect(request.shop.contact).not.toHaveProperty('address');
  });

  it('không sửa biểu mẫu đưa vào', () => {
    const form = valid('INDIVIDUAL', { legalName: '  Nguyễn A  ' });
    const snapshot = { ...form };

    buildSubmitRequest(form);

    expect(form).toEqual(snapshot);
  });

  it('chưa chọn loại hình: ném lỗi (biểu mẫu phải qua kiểm rồi mới dựng)', () => {
    expect(() => buildSubmitRequest({ ...valid('INDIVIDUAL'), sellerType: null })).toThrow();
  });
});

describe('formValueFromProfile (điền sẵn khi nộp lại, KT-08)', () => {
  const profile: SellerProfile = {
    id: 'p-1',
    status: 'REJECTED',
    sellerType: 'BUSINESS',
    legalInfo: {
      legalName: 'Công ty A',
      representativeName: 'Trần Thị B',
      identityNumber: '079123456789',
      taxCode: '0123456789',
      businessAddress: '12 Lê Lợi, Quận 1, TP.HCM',
    },
    shop: {
      shopName: 'Shop A',
      description: 'Bán đồ gia dụng',
      contact: { personName: 'Nguyễn A', phone: '0901234567', address: '34 Nguyễn Huệ, Quận 1, TP.HCM' },
    },
    rejectionReason: 'Giấy tờ mờ',
    submittedAt: '2026-11-10T08:00:00Z',
  };

  it('chép từng trường của hồ sơ vào đúng ô', () => {
    expect(formValueFromProfile(profile)).toStrictEqual({
      sellerType: 'BUSINESS',
      legalName: 'Công ty A',
      representativeName: 'Trần Thị B',
      identityNumber: '079123456789',
      taxCode: '0123456789',
      businessAddress: '12 Lê Lợi, Quận 1, TP.HCM',
      shopName: 'Shop A',
      description: 'Bán đồ gia dụng',
      contactPersonName: 'Nguyễn A',
      contactPhone: '0901234567',
      contactAddress: '34 Nguyễn Huệ, Quận 1, TP.HCM',
    });
  });

  it('trường vắng, null hoặc phần con vắng: ô là chuỗi rỗng, không ra "undefined" hay "null"', () => {
    const sparse: SellerProfile = {
      id: 'p-2',
      status: 'REJECTED',
      sellerType: 'INDIVIDUAL',
      legalInfo: { legalName: 'Nguyễn A', identityNumber: '079123456789', businessAddress: '12 Lê Lợi, Quận 1' },
      shop: { shopName: 'Shop A', description: null },
    };

    const form = formValueFromProfile(sparse);

    expect(form.description).toBe('');
    expect(form.contactPersonName).toBe('');
    expect(form.contactPhone).toBe('');
    expect(form.contactAddress).toBe('');
    expect(form.representativeName).toBe('');
    expect(form.taxCode).toBe('');
    expect(Object.values(form)).not.toContain(undefined);
    expect(Object.values(form)).not.toContain(null);
  });

  it('điền sẵn rồi dựng lại cho ra đúng yêu cầu ban đầu (vòng đi về)', () => {
    expect(buildSubmitRequest(formValueFromProfile(profile))).toStrictEqual({
      sellerType: profile.sellerType,
      legalInfo: profile.legalInfo,
      shop: profile.shop,
    });
  });
});

// ---------------------------------------------------------------------------------------------------------------------
// mapSubmitError: chiều về của biểu mẫu (lỗi máy chủ thành chỉ thị hiển thị)
// ---------------------------------------------------------------------------------------------------------------------

function http(status: number, body: unknown = null, headers: Record<string, string> = {}): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: body, headers: new HttpHeaders(headers) });
}

/** Thân lỗi đúng khuôn `ErrorResponse` của api.yaml. */
function failure(code: string, errors: string[] = [], message = 'Thông điệp của máy chủ'): object {
  return { success: false, code, message, errors, traceId: 't-1' };
}

function messageOf(view: SubmitErrorView): string {
  if (view.kind === 'ignore') throw new Error('không có thông báo');
  return view.message;
}

describe('mapSubmitError (KT-09)', () => {
  describe('mã đã ngầm chỉ ô: gắn vào ô, không cần errors', () => {
    it.each<[number, string, ProfileFormField]>([
      [409, 'SHOP_NAME_TAKEN', 'shopName'],
      [409, 'IDENTITY_ALREADY_REGISTERED', 'identityNumber'],
      [400, 'INVALID_IDENTITY_NUMBER', 'identityNumber'],
      [400, 'INVALID_TAX_CODE', 'taxCode'],
    ])('%i %s: ô %s', (status, code, field) => {
      const view = mapSubmitError(http(status, failure(code)));

      expect(view).toMatchObject({ kind: 'field', field });
      expect(messageOf(view).length).toBeGreaterThan(0);
    });

    it('mã chỉ ô thắng errors lạ đi kèm', () => {
      expect(mapSubmitError(http(409, failure('SHOP_NAME_TAKEN', ['contact.phone'])))).toMatchObject({ kind: 'field', field: 'shopName' });
    });
  });

  describe('LEGAL_INFO_REQUIRED và SELLER_FIELD_INVALID: ô theo errors[0] (tên trường của máy chủ)', () => {
    it.each<[string, ProfileFormField]>([
      ['sellerType', 'sellerType'],
      ['legalName', 'legalName'],
      ['representativeName', 'representativeName'],
      ['identityNumber', 'identityNumber'],
      ['taxCode', 'taxCode'],
      ['businessAddress', 'businessAddress'],
      ['shopName', 'shopName'],
      ['description', 'description'],
      ['contact.personName', 'contactPersonName'],
      ['contact.phone', 'contactPhone'],
      ['contact.address', 'contactAddress'],
    ])('errors[0] = %s: ô %s', (serverField, field) => {
      expect(mapSubmitError(http(400, failure('SELLER_FIELD_INVALID', [serverField])))).toMatchObject({ kind: 'field', field });
      expect(mapSubmitError(http(400, failure('LEGAL_INFO_REQUIRED', [serverField])))).toMatchObject({ kind: 'field', field });
    });

    it.each([
      ['thiếu errors', []],
      ['tên trường lạ', ['contact']],
      ['tên trường không có ô', ['reason']],
    ])('%s: thông báo chung ở đầu biểu mẫu, không gắn vào ô', (_name, errors) => {
      const view = mapSubmitError(http(400, failure('SELLER_FIELD_INVALID', errors)));

      expect(view.kind).toBe('form');
      expect(messageOf(view).length).toBeGreaterThan(0);
    });
  });

  describe('trạng thái hồ sơ đã đổi ở nơi khác hoặc chưa rõ kết quả: làm mới trạng thái rồi về trang chủ', () => {
    it.each<[number, string]>([
      [409, 'SELLER_PROFILE_EXISTS'],
      [409, 'INVALID_STATE'],
      [409, 'CONCURRENT_UPDATE'],
      [404, 'SELLER_PROFILE_NOT_FOUND'],
    ])('%i %s', (status, code) => {
      const view = mapSubmitError(http(status, failure(code)));

      expect(view.kind).toBe('stale');
      expect(messageOf(view).length).toBeGreaterThan(0);
    });

    it('504: máy chủ có thể đã nhận hồ sơ, nên kiểm lại trạng thái trước khi cho gửi lại', () => {
      expect(mapSubmitError(http(504)).kind).toBe('stale');
    });

    it.each<[string, unknown]>([
      ['2xx nhưng thân sai khuôn (InvalidApiResponse)', new InvalidApiResponse('not-envelope')],
      ['2xx nhưng success:false', new InvalidApiResponse('success-false')],
      ['hết giờ chờ ở giao diện (TimeoutError)', new TimeoutError()],
      ['Angular báo HttpErrorResponse mang trạng thái 2xx (không đọc được thân)', http(200, 'không phải JSON')],
    ])('%s: hồ sơ có thể đã được tạo, nên kiểm lại trạng thái, không cho gửi lại ngay', (_name, error) => {
      const view = mapSubmitError(error);

      expect(view.kind).toBe('stale');
      expect(messageOf(view).length).toBeGreaterThan(0);
    });
  });

  describe('lỗi tạm thời: giữ nguyên dữ liệu đã nhập và cho gửi lại', () => {
    it.each<[string, HttpErrorResponse]>([
      ['500', http(500, failure('INTERNAL_ERROR'))],
      ['500 thân không phải JSON', http(500, 'oops')],
      ['502', http(502)],
      ['503', http(503)],
      ['mất mạng (status 0)', http(0)],
      ['429 quá nhiều yêu cầu', http(429, null, { 'Retry-After': '30' })],
      ['403 token CSRF vẫn sai sau khi bộ chặn đã thử lại', http(403, failure('CSRF_TOKEN_INVALID'))],
    ])('%s', (_name, error) => {
      const view = mapSubmitError(error);

      expect(view.kind).toBe('retry');
      expect(messageOf(view).length).toBeGreaterThan(0);
    });
  });

  describe('trường hợp còn lại', () => {
    it('401: bộ chặn chung đã đưa về đăng nhập, biểu mẫu không hiện gì thêm', () => {
      expect(mapSubmitError(http(401, failure('UNAUTHENTICATED')))).toEqual({ kind: 'ignore' });
    });

    it('403 SELLER_ROLE_NOT_ALLOWED (tài khoản quản trị): thông báo chung, không cho gửi lại như lỗi tạm', () => {
      const view = mapSubmitError(http(403, failure('SELLER_ROLE_NOT_ALLOWED')));

      expect(view.kind).toBe('form');
    });

    it.each([
      ['400 mã lạ', http(400, failure('MA_LA'))],
      ['400 không có mã', http(400, {})],
      ['422 mã lạ', http(422, failure('MA_LA'))],
    ])('%s: thông báo chung ở đầu biểu mẫu', (_name, error) => {
      expect(mapSubmitError(error).kind).toBe('form');
    });

    it.each([['Error thường', new Error('boom')], ['chuỗi', 'oops'], ['undefined', undefined], ['null', null]])(
      'giá trị không phải lỗi HTTP (%s): thông báo chung, không ném',
      (_name, error) => {
        expect(mapSubmitError(error).kind).toBe('form');
      },
    );
  });

  describe('bất biến', () => {
    it('không đưa chữ thông điệp của máy chủ lên màn hình (R6: không dựa vào chữ của máy chủ)', () => {
      const secret = 'LOI-NOI-BO-XYZ-079123456789';
      const errors = [
        http(409, failure('SHOP_NAME_TAKEN', [], secret)),
        http(400, failure('SELLER_FIELD_INVALID', ['shopName'], secret)),
        http(400, failure('SELLER_FIELD_INVALID', [], secret)),
        http(409, failure('SELLER_PROFILE_EXISTS', [], secret)),
        http(500, failure('INTERNAL_ERROR', [], secret)),
        http(400, failure('MA_LA', [], secret)),
      ];

      for (const error of errors) {
        const view = mapSubmitError(error);
        expect(JSON.stringify(view)).not.toContain('XYZ');
      }
    });

    it('một ô một thông báo: hai ô khác nhau không dùng chung câu chữ', () => {
      const shop = messageOf(mapSubmitError(http(409, failure('SHOP_NAME_TAKEN'))));
      const identity = messageOf(mapSubmitError(http(409, failure('IDENTITY_ALREADY_REGISTERED'))));

      expect(shop).not.toBe(identity);
    });
  });
});
