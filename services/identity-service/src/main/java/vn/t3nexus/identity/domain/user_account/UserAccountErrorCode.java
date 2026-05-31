package vn.t3nexus.identity.domain.user_account;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

public enum UserAccountErrorCode implements ErrorCode {

    USER_NOT_FOUND            ("10002", "User not found",                            "error.user.not_found",                 404),
    USER_NOT_ACTIVE           ("10003", "User is not active",                        "error.user.not_active",                400),
    ACCOUNT_LOCKED            ("10004", "Account is locked",                         "error.user.account_locked",            423),
    USER_ALREADY_LOCKED       ("10005", "User is already locked",                    "error.user.already_locked",            409),
    USER_NOT_LOCKED           ("10006", "User is not locked",                        "error.user.not_locked",                400),
    INVALID_STATUS_TRANSITION ("10010", "Invalid status transition for operation",   "error.user.invalid_status_transition", 422),
    RESEND_NOT_ALLOWED        ("10011", "Verification resend not applicable",        "error.user.resend_not_allowed",        400),
    RATE_LIMIT_EXCEEDED       ("10012", "Too many requests, please try again later", "error.user.rate_limit_exceeded",       429);

    private final String code;
    private final String defaultMessage;
    private final String messageKey;
    private final int httpStatus;

    UserAccountErrorCode(String code, String defaultMessage, String messageKey, int httpStatus) {
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
