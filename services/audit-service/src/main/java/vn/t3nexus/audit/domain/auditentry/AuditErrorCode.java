package vn.t3nexus.audit.domain.auditentry;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

/**
 * Mã lỗi nghiệp vụ cho miền nhật ký kiểm toán.
 * <br>Bảo đảm tính nhất quán của mã phản hồi HTTP và thông báo lỗi.
 */
public enum AuditErrorCode implements ErrorCode {

    AUDIT_ENTRY_NOT_FOUND   ("50001", "Không tìm thấy bản ghi kiểm toán",             "error.audit.entry_not_found",    404),
    AUDIT_LIMIT_INVALID     ("50002", "Cỡ trang phải từ 1 đến 200",                   "error.audit.limit_invalid",      400),
    AUDIT_CURSOR_INVALID    ("50003", "Con trỏ trang không hợp lệ",                   "error.audit.cursor_invalid",     400),
    AUDIT_TIME_RANGE_INVALID("50004", "Thời điểm bắt đầu phải trước thời điểm kết thúc", "error.audit.time_range_invalid", 400),
    AUDIT_FILTER_INVALID    ("50005", "Bộ lọc không hợp lệ",                         "error.audit.filter_invalid",     400),
    AUDIT_PARAMETER_INVALID ("50006", "Tham số không đúng định dạng",                 "error.audit.parameter_invalid",  400);

    private final String code;
    private final String defaultMessage;
    private final String messageKey;
    private final int httpStatus;

    AuditErrorCode(String code, String defaultMessage, String messageKey, int httpStatus) {
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
