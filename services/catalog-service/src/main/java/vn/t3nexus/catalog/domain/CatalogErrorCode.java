package vn.t3nexus.catalog.domain;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

/**
 * Mã lỗi dùng chung của danh mục hàng. Mã chữ là tên hằng; 404 không có, 409 vi phạm quy tắc hoặc trạng thái,
 * 400 dữ liệu vào sai, 403 sai vai.
 */
public enum CatalogErrorCode implements ErrorCode {

    CATALOG_ROLE_NOT_ALLOWED ("CATALOG_ROLE_NOT_ALLOWED", "Vai trò người dùng không được phép",     "error.catalog.role_not_allowed", 403),
    CATALOG_FIELD_INVALID    ("CATALOG_FIELD_INVALID",    "Trường dữ liệu không hợp lệ",             "error.catalog.field_invalid",    400),
    CATALOG_INVALID_STATE    ("CATALOG_INVALID_STATE",    "Trạng thái hiện tại không cho phép thao tác", "error.catalog.invalid_state", 409),
    CONCURRENT_UPDATE        ("CONCURRENT_UPDATE",        "Dữ liệu đã bị cập nhật đồng thời",        "error.catalog.concurrent_update", 409);

    private final String code;
    private final String defaultMessage;
    private final String messageKey;
    private final int httpStatus;

    CatalogErrorCode(String code, String defaultMessage, String messageKey, int httpStatus) {
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
