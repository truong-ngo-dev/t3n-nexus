package vn.t3nexus.lib.upload;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

public enum UploadErrorCode implements ErrorCode {

    UPLOAD_TYPE_NOT_ALLOWED     ("UPLOAD_TYPE_NOT_ALLOWED",     "Loại tệp không được phép",                 "error.upload.type_not_allowed",        400),
    UPLOAD_SIZE_INVALID         ("UPLOAD_SIZE_INVALID",         "Dung lượng tệp không hợp lệ",             "error.upload.size_invalid",            400),
    UPLOAD_SLOT_NOT_FOUND       ("UPLOAD_SLOT_NOT_FOUND",       "Không tìm thấy chỗ tải lên",               "error.upload.slot_not_found",          404),
    UPLOAD_SLOT_EXPIRED         ("UPLOAD_SLOT_EXPIRED",         "Chỗ tải lên đã hết hạn",                  "error.upload.slot_expired",            409),
    UPLOAD_PENDING_LIMIT        ("UPLOAD_PENDING_LIMIT",        "Quá nhiều chỗ tải lên đang chờ",           "error.upload.pending_limit",           409),
    UPLOAD_NOT_COMPLETED        ("UPLOAD_NOT_COMPLETED",        "Tệp chưa được tải lên",                   "error.upload.not_completed",           422),
    UPLOAD_CONTENT_INVALID      ("UPLOAD_CONTENT_INVALID",      "Nội dung tệp không đúng loại cho phép",   "error.upload.content_invalid",         422),
    UPLOAD_STORAGE_UNAVAILABLE  ("UPLOAD_STORAGE_UNAVAILABLE",  "Kho đối tượng không phản hồi",             "error.upload.storage_unavailable",     503);

    private final String code;
    private final String defaultMessage;
    private final String messageKey;
    private final int httpStatus;

    UploadErrorCode(String code, String defaultMessage, String messageKey, int httpStatus) {
        this.code           = code;
        this.defaultMessage = defaultMessage;
        this.messageKey     = messageKey;
        this.httpStatus     = httpStatus;
    }

    @Override public String code()           { return code; }
    @Override public String defaultMessage() { return defaultMessage; }
    @Override public String messageKey()     { return messageKey; }
    @Override public int httpStatus()        { return httpStatus; }
}
