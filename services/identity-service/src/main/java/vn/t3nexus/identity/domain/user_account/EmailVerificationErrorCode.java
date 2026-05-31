package vn.t3nexus.identity.domain.user_account;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

public enum EmailVerificationErrorCode implements ErrorCode {

    VERIFICATION_NOT_FOUND  ("20001", "Verification not found",          "error.verification.not_found",    404),
    VERIFICATION_EXPIRED    ("20002", "Verification token has expired",  "error.verification.expired",      410),
    VERIFICATION_INVALID    ("20003", "Verification token is invalid",   "error.verification.invalid",      400),
    ALREADY_VERIFIED        ("20004", "Email is already verified",       "error.verification.already_done", 409);

    private final String code;
    private final String defaultMessage;
    private final String messageKey;
    private final int httpStatus;

    EmailVerificationErrorCode(String code, String defaultMessage, String messageKey, int httpStatus) {
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
