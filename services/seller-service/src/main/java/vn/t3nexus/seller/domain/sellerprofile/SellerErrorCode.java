package vn.t3nexus.seller.domain.sellerprofile;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

/**
 * Mã lỗi nghiệp vụ cho miền hồ sơ người bán.
 */
public enum SellerErrorCode implements ErrorCode {

    LEGAL_INFO_REQUIRED       ("LEGAL_INFO_REQUIRED",        "Thông tin pháp lý là bắt buộc",       "error.seller.legal_info_required",        400),
    INVALID_IDENTITY_NUMBER   ("INVALID_IDENTITY_NUMBER",    "Số căn cước không hợp lệ",             "error.seller.invalid_identity_number",    400),
    INVALID_TAX_CODE          ("INVALID_TAX_CODE",           "Mã số thuế không hợp lệ",              "error.seller.invalid_tax_code",           400),
    SELLER_FIELD_INVALID      ("SELLER_FIELD_INVALID",       "Trường dữ liệu không hợp lệ",          "error.seller.seller_field_invalid",       400),
    SELLER_ROLE_NOT_ALLOWED   ("SELLER_ROLE_NOT_ALLOWED",    "Vai trò người dùng không được phép",   "error.seller.seller_role_not_allowed",    403),
    SELLER_PROFILE_NOT_FOUND  ("SELLER_PROFILE_NOT_FOUND",   "Không tìm thấy hồ sơ người bán",      "error.seller.seller_profile_not_found",   404),
    SELLER_PROFILE_EXISTS     ("SELLER_PROFILE_EXISTS",      "Hồ sơ người bán đã tồn tại",          "error.seller.seller_profile_exists",      409),
    SHOP_NAME_TAKEN           ("SHOP_NAME_TAKEN",            "Tên gian hàng đã được sử dụng",        "error.seller.shop_name_taken",            409),
    IDENTITY_ALREADY_REGISTERED("IDENTITY_ALREADY_REGISTERED","Thông tin định danh đã được đăng ký", "error.seller.identity_already_registered", 409),
    LEGAL_INFO_LOCKED         ("LEGAL_INFO_LOCKED",          "Thông tin pháp lý đã bị khóa",         "error.seller.legal_info_locked",          409),
    INVALID_STATE             ("INVALID_STATE",              "Trạng thái hồ sơ không hợp lệ",        "error.seller.invalid_state",              409),
    CONCURRENT_UPDATE         ("CONCURRENT_UPDATE",          "Dữ liệu đã bị cập nhật đồng thời",     "error.seller.concurrent_update",          409),
    STALE_VERSION             ("STALE_VERSION",              "Hồ sơ đã thay đổi sau khi bạn xem, hãy xem lại bản mới", "error.seller.stale_version", 409),
    REJECTION_REASON_REQUIRED ("REJECTION_REASON_REQUIRED",  "Lý do từ chối là bắt buộc",            "error.seller.rejection_reason_required", 400);

    private final String code;
    private final String defaultMessage;
    private final String messageKey;
    private final int httpStatus;

    SellerErrorCode(String code, String defaultMessage, String messageKey, int httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.messageKey = messageKey;
        this.httpStatus = httpStatus;
    }

    @Override public String code()           { return code; }
    @Override public String defaultMessage() { return defaultMessage; }
    @Override public String messageKey()     { return messageKey; }
    @Override public int httpStatus()        { return httpStatus; }
}
