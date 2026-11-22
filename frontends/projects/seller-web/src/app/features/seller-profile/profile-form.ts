import {classifyFailure, toErrorInfo} from '@t3n/http';
import {LegalInfo, SellerProfile, SellerType} from '../../core/service/seller-profile/seller-profile.model';
import {SubmitSellerProfileRequest, UpdateShopInfoRequest} from './data/seller-profile-command.model';


// ---------------------------------------------------------------------------------------------------------------------
// Chế độ biểu mẫu: một thành phần cho bốn việc, khác nhau tra ở FORM_MODES (design.md mục 2)
// ---------------------------------------------------------------------------------------------------------------------

/**
 * - `submit`: nộp hồ sơ lần đầu (`/profile/new`, chưa có hồ sơ).
 * - `edit-pending`: sửa toàn bộ khi chờ duyệt (`/profile/edit`), `PUT /profile`.
 * - `resubmit`: sửa và nộp lại khi bị từ chối (`/profile/resubmit`).
 * - `edit-approved`: sửa thông tin gian hàng khi đã duyệt (`/profile/edit-shop`); tên gian hàng và pháp lý đã khóa.
 */
export type ProfileFormMode = 'submit' | 'edit-pending' | 'resubmit' | 'edit-approved';

/** Dòng ở đầu biểu mẫu: lời nhắc khi chờ duyệt, lý do từ chối, hoặc khối "đã khóa" khi đã duyệt. */
export type FormNotice = 'pending' | 'rejection' | 'locked' | null;

export interface FormModeConfig {
  title: string;
  submitLabel: string;
  notice: FormNotice;
  /** Tải hồ sơ để điền sẵn. */
  loads: boolean;
  /** Chưa đổi gì thì khóa nút gửi. Nộp lại không khóa: nộp lại nguyên bản vẫn là xin xét lại. */
  requireDirty: boolean;
  /** Trạng thái hồ sơ đổi sau khi gửi, nên làm mới trạng thái dùng chung TRƯỚC khi chuyển trang (guard của `/profile`). */
  refreshAfter: boolean;
  successMessage: string;
  /** Rời về đâu khi bị `stale` (Q6): nộp lần đầu có thể chưa có hồ sơ để xem nên về trang chủ. */
  leaveTo: '/' | '/profile';
  cancelTo: '/' | '/profile';
}

export const FORM_MODES: Record<ProfileFormMode, FormModeConfig> = {
  'submit': {
    title: 'Nộp hồ sơ người bán', submitLabel: 'Nộp hồ sơ', notice: null, loads: false, requireDirty: false, refreshAfter: true,
    successMessage: 'Đã nộp hồ sơ', leaveTo: '/', cancelTo: '/',
  },
  'edit-pending': {
    title: 'Sửa hồ sơ', submitLabel: 'Lưu thay đổi', notice: 'pending', loads: true, requireDirty: true, refreshAfter: false,
    successMessage: 'Đã lưu thay đổi', leaveTo: '/profile', cancelTo: '/profile',
  },
  'resubmit': {
    title: 'Sửa và nộp lại hồ sơ', submitLabel: 'Nộp lại hồ sơ', notice: 'rejection', loads: true, requireDirty: false, refreshAfter: true,
    successMessage: 'Đã nộp lại hồ sơ', leaveTo: '/profile', cancelTo: '/profile',
  },
  'edit-approved': {
    title: 'Sửa thông tin gian hàng', submitLabel: 'Lưu thay đổi', notice: 'locked', loads: true, requireDirty: true, refreshAfter: false,
    successMessage: 'Đã lưu thay đổi', leaveTo: '/profile', cancelTo: '/profile',
  },
};

/** Ô sửa được khi đã duyệt. Tên gian hàng, loại hình và pháp lý đã khóa (srs mục 3). */
const APPROVED_EDITABLE_FIELDS = ['description', 'contactPersonName', 'contactPhone', 'contactAddress'] as const;

/**
 * Ô hiện thành ô nhập và được kiểm ở một chế độ, đúng thứ tự trên màn hình. Ba chế độ đầy đủ: loại hình, ô pháp lý theo loại hình,
 * rồi gian hàng và liên hệ. `edit-approved`: chỉ bốn ô của {@link APPROVED_EDITABLE_FIELDS}.
 */
export function fieldsOf(mode: ProfileFormMode, type: SellerType | null): readonly ProfileFormField[] {
  if (mode === 'edit-approved') return APPROVED_EDITABLE_FIELDS;
  return ['sellerType', ...legalFieldsFor(type), 'shopName', 'description', 'contactPersonName', 'contactPhone', 'contactAddress'];
}

/**
 * Giá trị của biểu mẫu hồ sơ: 11 ô phẳng (chuỗi, riêng loại hình là `null` khi chưa chọn). Phẳng để nối thẳng với `form()` của Signal Forms
 * và với các hàm thuần dưới đây.
 *
 * Tên ô -> chỗ nó đi vào yêu cầu gửi lên -> tên mà máy chủ dùng khi báo lỗi trường (`errors[0]`):
 *   sellerType         -> sellerType                    -> sellerType
 *   legalName          -> legalInfo.legalName           -> legalName
 *   representativeName -> legalInfo.representativeName  -> representativeName   (chỉ doanh nghiệp)
 *   identityNumber     -> legalInfo.identityNumber      -> identityNumber
 *   taxCode            -> legalInfo.taxCode             -> taxCode
 *   businessAddress    -> legalInfo.businessAddress     -> businessAddress
 *   shopName           -> shop.shopName                 -> shopName
 *   description        -> shop.description              -> description
 *   contactPersonName  -> shop.contact.personName       -> contact.personName
 *   contactPhone       -> shop.contact.phone            -> contact.phone
 *   contactAddress     -> shop.contact.address          -> contact.address
 */
export interface ProfileFormValue {
  sellerType: SellerType | null;
  legalName: string;
  representativeName: string;
  identityNumber: string;
  taxCode: string;
  businessAddress: string;
  shopName: string;
  description: string;
  contactPersonName: string;
  contactPhone: string;
  contactAddress: string;
}

export type ProfileFormField = keyof ProfileFormValue;

/** Ô pháp lý: trùng tên với khóa của `LegalInfo`, nên chép từ ô sang yêu cầu được theo khóa. */
type LegalField = keyof LegalInfo;

const LEGAL_FIELDS: Record<SellerType, readonly LegalField[]> = {
  INDIVIDUAL: ['legalName', 'identityNumber', 'taxCode', 'businessAddress'],
  HOUSEHOLD: ['legalName', 'identityNumber', 'taxCode', 'businessAddress'],
  BUSINESS: ['legalName', 'representativeName', 'identityNumber', 'taxCode', 'businessAddress'],
};

export function emptyProfileForm(): ProfileFormValue {
  return {
    sellerType: null,
    legalName: '',
    representativeName: '',
    identityNumber: '',
    taxCode: '',
    businessAddress: '',
    shopName: '',
    description: '',
    contactPersonName: '',
    contactPhone: '',
    contactAddress: '',
  };
}

/** Ô pháp lý hiện (và được gửi) theo loại hình, đúng thứ tự trên màn hình. Chưa chọn loại hình: chưa hiện ô nào. */
export function legalFieldsFor(type: SellerType | null): readonly LegalField[] {
  return type === null ? [] : LEGAL_FIELDS[type];
}

/** Bỏ khoảng trắng hai đầu; rỗng thì trả `undefined` để ô tùy chọn vắng hẳn khỏi yêu cầu. Việc chuẩn hóa sâu hơn là của máy chủ. */
function clean(text: string): string | undefined {
  return text.trim() || undefined;
}

/**
 * Dựng yêu cầu nộp hoặc nộp lại từ giá trị biểu mẫu. Biểu mẫu phải qua kiểm rồi mới dựng: chưa chọn loại hình thì ném lỗi.
 * Ô không hiện theo loại hình (người đại diện của cá nhân và hộ) không được gửi dù còn giá trị rác; ô tùy chọn để trống thì vắng hẳn.
 */
export function buildSubmitRequest(value: ProfileFormValue): SubmitSellerProfileRequest {
  const {sellerType} = value;
  if (sellerType === null) throw new Error('Chưa chọn loại hình');

  const legalInfo: LegalInfo = {};
  for (const field of legalFieldsFor(sellerType)) {
    const text = clean(value[field]);
    if (text !== undefined) legalInfo[field] = text;
  }

  const description = clean(value.description);
  const address = clean(value.contactAddress);

  return {
    sellerType,
    legalInfo,
    shop: {
      shopName: value.shopName.trim(),
      ...(description && {description}),
      contact: {
        personName: value.contactPersonName.trim(),
        phone: value.contactPhone.trim(),
        ...(address && {address}),
      },
    },
  };
}

/** Điền sẵn biểu mẫu từ hồ sơ (nộp lại khi bị từ chối). Trường vắng hoặc `null` thành chuỗi rỗng. */
export function formValueFromProfile(profile: SellerProfile): ProfileFormValue {
  const {legalInfo, shop} = profile;
  return {
    sellerType: profile.sellerType,
    legalName: legalInfo.legalName ?? '',
    representativeName: legalInfo.representativeName ?? '',
    identityNumber: legalInfo.identityNumber ?? '',
    taxCode: legalInfo.taxCode ?? '',
    businessAddress: legalInfo.businessAddress ?? '',
    shopName: shop.shopName ?? '',
    description: shop.description ?? '',
    contactPersonName: shop.contact?.personName ?? '',
    contactPhone: shop.contact?.phone ?? '',
    contactAddress: shop.contact?.address ?? '',
  };
}

/**
 * Thân `PATCH /profile/shop-info` khi đã duyệt: chỉ các ô sửa được (không bao giờ có `shopName`) mà giá trị đã cắt khoảng trắng khác
 * ban đầu. Ô tùy chọn bị xóa trắng thành `null` để máy chủ xóa giá trị cũ (Q7); ô bắt buộc trống không tới được đây vì đã bị kiểm chặn.
 *
 * @returns thân gửi lên, hoặc `null` nếu không có ô nào đổi
 */
export function buildShopInfoPatch(original: ProfileFormValue, current: ProfileFormValue): UpdateShopInfoRequest | null {
  const changed = (field: ProfileFormField) => String(current[field] ?? '').trim() !== String(original[field] ?? '').trim();
  const optional = (text: string) => clean(text) ?? null;

  const patch: UpdateShopInfoRequest = {};
  if (changed('description')) patch.description = optional(current.description);

  const contact: NonNullable<UpdateShopInfoRequest['contact']> = {};
  if (changed('contactPersonName')) contact.personName = current.contactPersonName.trim();
  if (changed('contactPhone')) contact.phone = current.contactPhone.trim();
  if (changed('contactAddress')) contact.address = optional(current.contactAddress);
  if (Object.keys(contact).length > 0) patch.contact = contact;

  return Object.keys(patch).length > 0 ? patch : null;
}

/**
 * Đã đổi gì chưa, để khóa nút gửi ở hai chế độ sửa. So **thân sẽ gửi**, không so từng ô: " Tên " bằng "Tên", và ô của loại hình khác
 * đang bị ẩn (người đại diện khi là cá nhân) không tính. Hai chế độ nộp luôn trả `true` (không khóa theo đổi).
 */
export function isDirty(mode: ProfileFormMode, original: ProfileFormValue | null, current: ProfileFormValue): boolean {
  if (!FORM_MODES[mode].requireDirty || original === null) return true;
  if (mode === 'edit-approved') return buildShopInfoPatch(original, current) !== null;
  if (current.sellerType === null) return true;
  return JSON.stringify(buildSubmitRequest(original)) !== JSON.stringify(buildSubmitRequest(current));
}

// ---------------------------------------------------------------------------------------------------------------------
// Kiểm biểu mẫu (phía giao diện, trước khi gửi)
// ---------------------------------------------------------------------------------------------------------------------

/** Ô nhập chữ: mọi ô trừ loại hình (chọn một trong ba). */
type TextField = Exclude<ProfileFormField, 'sellerType'>;

/** Lý do một ô không đạt: `required` là thiếu, `invalid` là có giá trị nhưng sai độ dài hoặc định dạng. */
export type FieldError = 'required' | 'invalid';

/** Kết quả kiểm: chỉ chứa ô có lỗi. Rỗng (`{}`) nghĩa là biểu mẫu hợp lệ. */
export type ProfileFormErrors = Partial<Record<ProfileFormField, FieldError>>;

/**
 * Ô có bắt buộc không, để mẫu tự thêm dấu `*` (hoặc chữ "tùy chọn") và để kiểm. Ô pháp lý chỉ bắt buộc khi đang hiện theo loại hình
 * ({@link legalFieldsFor}); riêng mã số thuế của cá nhân là tùy chọn. Chưa chọn loại hình thì ô pháp lý chưa hiện nên không bắt buộc.
 *
 * @param type  loại hình đang chọn, `null` nếu chưa chọn
 * @param field ô cần hỏi
 */
export function isFieldRequired(type: SellerType | null, field: ProfileFormField): boolean {
  switch (field) {
    case 'sellerType':
    case 'shopName':
    case 'contactPersonName':
    case 'contactPhone':
      return true;
    case 'description':
    case 'contactAddress':
      return false;
    default:
      // ô pháp lý
      if (!legalFieldsFor(type).includes(field)) return false;
      return !(field === 'taxCode' && type === 'INDIVIDUAL');
  }
}

/** Tên pháp lý theo loại hình: cá nhân là họ tên, hộ kinh doanh là tên chủ hộ, doanh nghiệp là tên doanh nghiệp. */
const LEGAL_NAME_LABELS: Record<SellerType, string> = {
  INDIVIDUAL: 'Họ tên',
  HOUSEHOLD: 'Tên chủ hộ',
  BUSINESS: 'Tên doanh nghiệp',
};

/**
 * Nhãn của ô. Chỉ tên ô: dấu `*` hay chữ "tùy chọn" do mẫu tự thêm theo {@link isFieldRequired}. Tên pháp lý và địa chỉ kinh doanh đổi
 * theo loại hình; chưa chọn loại hình vẫn trả nhãn dùng được (không ném).
 *
 * @param type  loại hình đang chọn, `null` nếu chưa chọn
 * @param field ô cần nhãn
 */
export function fieldLabel(type: SellerType | null, field: ProfileFormField): string {
  switch (field) {
    case 'sellerType': return 'Loại hình';
    case 'legalName': return type === null ? 'Tên pháp lý' : LEGAL_NAME_LABELS[type];
    case 'representativeName': return 'Người đại diện';
    case 'identityNumber': return 'Số căn cước';
    case 'taxCode': return 'Mã số thuế';
    case 'businessAddress': return type === 'BUSINESS' ? 'Địa chỉ trụ sở' : 'Địa chỉ kinh doanh';
    case 'shopName': return 'Tên gian hàng';
    case 'description': return 'Mô tả gian hàng';
    case 'contactPersonName': return 'Người liên hệ';
    case 'contactPhone': return 'Số điện thoại';
    case 'contactAddress': return 'Địa chỉ liên hệ';
  }
}

/** Bỏ khoảng trắng hai đầu và gộp khoảng trắng liền nhau thành một, như máy chủ làm trước khi đếm độ dài. */
function squash(text: string): string {
  return text.trim().replace(/\s+/g, ' ');
}

/** Số căn cước: đúng 12 chữ số sau khi bỏ mọi khoảng trắng (máy chủ cũng bỏ mọi khoảng trắng trước khi kiểm). */
function isIdentityNumber(text: string): boolean {
  return /^\d{12}$/.test(text.replace(/\s/g, ''));
}

/** Mã số thuế: 10 chữ số, có thể kèm 3 chữ số chi nhánh (liền hoặc sau dấu gạch ngang), sau khi bỏ mọi khoảng trắng. */
function isTaxCode(text: string): boolean {
  return /^\d{10}(-?\d{3})?$/.test(text.replace(/\s/g, ''));
}

/**
 * Số điện thoại di động Việt Nam: bỏ khoảng trắng, dấu chấm, gạch ngang và ngoặc; đổi đầu `+84` hoặc `84` thành `0`; rồi phải khớp
 * `0[35789]` cộng 8 chữ số.
 */
function isPhone(text: string): boolean {
  const digits = text.replace(/[\s.\-()]/g, '');
  const national = digits.startsWith('+84') ? `0${digits.slice(3)}` : digits.startsWith('84') ? `0${digits.slice(2)}` : digits;
  return /^0[35789]\d{8}$/.test(national);
}

/** Điều kiện độ dài (tính trên chuỗi đã {@link squash}), cả hai đầu đều nhận. */
function inLength(min: number, max: number): (text: string) => boolean {
  return (text) => text.length >= min && text.length <= max;
}

/** Quy tắc định dạng của từng ô, áp lên giá trị **không rỗng** đã {@link squash}. Khớp quy tắc của máy chủ (`api.yaml`). */
const FORMAT_RULES: Record<TextField, (text: string) => boolean> = {
  legalName: inLength(2, 150),
  representativeName: inLength(2, 100),
  identityNumber: isIdentityNumber,
  taxCode: isTaxCode,
  businessAddress: inLength(10, 300),
  shopName: inLength(3, 60),
  description: inLength(1, 500),
  contactPersonName: inLength(2, 100),
  contactPhone: isPhone,
  contactAddress: inLength(10, 300),
};

/** Kiểm một ô chữ: rỗng thì `required` (nếu bắt buộc) hoặc hợp lệ (nếu tùy chọn); có giá trị thì phải đúng quy tắc, nếu không là `invalid`. */
function fieldError(type: SellerType | null, field: TextField, raw: string): FieldError | undefined {
  const text = squash(raw);
  if (text === '') return isFieldRequired(type, field) ? 'required' : undefined;
  return FORMAT_RULES[field](text) ? undefined : 'invalid';
}

/**
 * Kiểm cả biểu mẫu, báo **đủ** từng ô có lỗi (không dừng ở lỗi đầu). Hàm thuần, không sửa đầu vào, là nguồn sự thật duy nhất
 * của luật kiểm phía giao diện (không dựa vào `valid()` của Signal Forms vì nó luôn `true` khi chưa đăng ký luật).
 *
 * Chỉ kiểm ô đang hiện: chưa chọn loại hình thì chỉ báo thiếu loại hình, ô pháp lý không hiện (như người đại diện của cá nhân và hộ)
 * không bị kiểm dù còn giá trị rác.
 *
 * @param value giá trị biểu mẫu hiện tại
 * @param mode  chế độ; chỉ kiểm các ô {@link fieldsOf} của chế độ đó (mặc định: đầy đủ như khi nộp)
 * @returns các ô có lỗi; `{}` nếu hợp lệ
 */
export function validateProfileForm(value: ProfileFormValue, mode: ProfileFormMode = 'submit'): ProfileFormErrors {
  const errors: ProfileFormErrors = {};
  const {sellerType} = value;
  for (const field of fieldsOf(mode, sellerType)) {
    if (field === 'sellerType') {
      if (sellerType === null) errors.sellerType = 'required';
      continue;
    }
    const error = fieldError(sellerType, field, value[field]);
    if (error) errors[field] = error;
  }
  return errors;
}

/**
 * Câu hiển thị dưới ô cho một lỗi kiểm phía giao diện. Hàm thuần; câu do giao diện tự đặt theo nhãn của ô, cùng lối với lỗi do máy chủ
 * từ chối ({@link mapSubmitError}), để người dùng thấy cùng một câu dù lỗi do bên nào phát hiện.
 *
 * @param type  loại hình đang chọn, `null` nếu chưa chọn (nhãn của một số ô đổi theo loại hình)
 * @param field ô có lỗi
 * @param error lý do: `required` là thiếu, `invalid` là có giá trị nhưng sai độ dài hoặc định dạng
 */
export function fieldErrorMessage(type: SellerType | null, field: ProfileFormField, error: FieldError): string {
  const label = fieldLabel(type, field);
  return error === 'required' ? `${label} là bắt buộc.` : `${label} không hợp lệ.`;
}

// ---------------------------------------------------------------------------------------------------------------------
// Lỗi nộp hồ sơ: từ lỗi nhận được thành chỉ thị hiển thị
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Chỉ thị hiển thị mà component làm theo khi nộp hoặc nộp lại thất bại. Mọi `message` do giao diện tự đặt, không lấy chữ của máy chủ (R6).
 * - `field`: hiện `message` dưới ô `field`.
 * - `form`: hiện `message` ở đầu biểu mẫu, không cho gửi lại như lỗi tạm.
 * - `retry`: hiện `message` ở đầu biểu mẫu, **giữ dữ liệu đã nhập** và cho gửi lại.
 * - `stale`: trạng thái hồ sơ đã đổi hoặc chưa rõ kết quả; báo `message` (snackbar), làm mới trạng thái rồi về trang chủ. **Không cho gửi lại.**
 * - `ignore`: không làm gì (bộ chặn 401 đã đưa về trang đăng nhập).
 */
export type SubmitErrorView =
  | { kind: 'field'; field: ProfileFormField; message: string }
  | { kind: 'form'; message: string }
  | { kind: 'retry'; message: string }
  | { kind: 'stale'; message: string }
  | { kind: 'ignore' };

/** Mã lỗi nghiệp vụ ngầm chỉ một ô: ô và câu hiển thị. */
const FIELD_BY_CODE: Record<string, { field: ProfileFormField; message: string }> = {
  SHOP_NAME_TAKEN: {field: 'shopName', message: 'Tên gian hàng đã được sử dụng. Vui lòng chọn tên khác.'},
  IDENTITY_ALREADY_REGISTERED: {field: 'identityNumber', message: 'Số căn cước này đã được đăng ký cho một hồ sơ khác.'},
  INVALID_IDENTITY_NUMBER: {field: 'identityNumber', message: 'Số căn cước không hợp lệ.'},
  INVALID_TAX_CODE: {field: 'taxCode', message: 'Mã số thuế không hợp lệ.'},
};

/** Tên trường máy chủ báo ở `errors[0]` thành ô của biểu mẫu. Tên không có ở đây thì không gắn được vào ô nào. */
const FIELD_BY_SERVER_NAME: Record<string, ProfileFormField> = {
  sellerType: 'sellerType',
  legalName: 'legalName',
  representativeName: 'representativeName',
  identityNumber: 'identityNumber',
  taxCode: 'taxCode',
  businessAddress: 'businessAddress',
  shopName: 'shopName',
  description: 'description',
  'contact.personName': 'contactPersonName',
  'contact.phone': 'contactPhone',
  'contact.address': 'contactAddress',
};

/** Mã nghiệp vụ nghĩa là trạng thái hồ sơ ở máy chủ không còn khớp với điều người dùng đang làm. */
const STALE_CODES: ReadonlySet<string> = new Set(['SELLER_PROFILE_EXISTS', 'INVALID_STATE', 'CONCURRENT_UPDATE', 'SELLER_PROFILE_NOT_FOUND']);

const MESSAGE_STALE = 'Trạng thái hồ sơ đã thay đổi. Đây là trạng thái hiện tại của hồ sơ bạn.';
const MESSAGE_UNKNOWN_OUTCOME = 'Chưa xác nhận được kết quả. Đây là trạng thái hiện tại của hồ sơ bạn.';
const MESSAGE_RETRY = 'Hệ thống đang bận hoặc mất kết nối. Dữ liệu của bạn vẫn được giữ, vui lòng thử lại sau ít phút.';
const MESSAGE_ROLE_NOT_ALLOWED = 'Tài khoản này không thể nộp hồ sơ người bán.';
const MESSAGE_GENERIC = 'Không thể nộp hồ sơ. Vui lòng kiểm tra lại thông tin hoặc thử lại sau.';
const MESSAGE_SHOP_NAME_LOCKED = 'Tên gian hàng đã khóa sau khi được duyệt.';

/** Dịch lỗi bị máy chủ từ chối (4xx) thành chỉ thị. Mã đã ngầm chỉ ô thắng `errors`; mã lạ thì rơi về thông báo chung. */
function viewOfRejected(error: unknown): SubmitErrorView {
  const {code, errors} = toErrorInfo(error);
  if (code === undefined) return {kind: 'form', message: MESSAGE_GENERIC};

  const byCode = FIELD_BY_CODE[code];
  if (byCode) return {kind: 'field', ...byCode};

  if (code === 'LEGAL_INFO_REQUIRED' || code === 'SELLER_FIELD_INVALID') {
    const serverName = errors?.[0];
    const field = serverName === undefined ? undefined : FIELD_BY_SERVER_NAME[serverName];
    if (field) {
      const label = fieldLabel(null, field);
      return {kind: 'field', field, message: code === 'LEGAL_INFO_REQUIRED' ? `${label} là bắt buộc.` : `${label} không hợp lệ.`};
    }
    return {kind: 'form', message: MESSAGE_GENERIC};
  }

  if (STALE_CODES.has(code)) return {kind: 'stale', message: MESSAGE_STALE};
  if (code === 'SELLER_ROLE_NOT_ALLOWED') return {kind: 'form', message: MESSAGE_ROLE_NOT_ALLOWED};
  if (code === 'SHOP_NAME_LOCKED') return {kind: 'form', message: MESSAGE_SHOP_NAME_LOCKED};
  return {kind: 'form', message: MESSAGE_GENERIC};
}

/**
 * Dịch lỗi của lệnh nộp hoặc nộp lại thành {@link SubmitErrorView}. Phân loại chung do `classifyFailure` (`@t3n/http`) làm, hàm này chỉ
 * thêm phần riêng của hồ sơ:
 *
 * | Loại (`classifyFailure`) | Chỉ thị |
 * |---|---|
 * | `handled` (401) | `ignore` |
 * | `transient` (mạng, 429, 502, 503, 500, CSRF sau thử lại) | `retry`: chưa ghi, giữ dữ liệu, cho gửi lại |
 * | `unknown-outcome` (504, hết giờ, thân 2xx hỏng) | `stale`: có thể đã ghi, kiểm lại trạng thái, **không** cho gửi lại ngay |
 * | `rejected` (4xx) | theo `code`: `field`, `stale` hoặc `form` |
 * | `unexpected` (lỗi lập trình, không phải lỗi HTTP) | `form` |
 *
 * Không ném với đầu vào lạ và không bao giờ đưa chữ thông điệp của máy chủ vào kết quả (R6).
 *
 * @param error lỗi nhận được từ lời gọi, còn `unknown`
 */
export function mapSubmitError(error: unknown): SubmitErrorView {
  switch (classifyFailure(error)) {
    case 'handled': return {kind: 'ignore'};
    case 'transient': return {kind: 'retry', message: MESSAGE_RETRY};
    case 'unknown-outcome': return {kind: 'stale', message: MESSAGE_UNKNOWN_OUTCOME};
    case 'rejected': return viewOfRejected(error);
    case 'unexpected': return {kind: 'form', message: MESSAGE_GENERIC};
  }
}
