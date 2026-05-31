package vn.t3nexus.oauth2.domain.user_credential;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

public enum UserCredentialErrorCode implements ErrorCode {

    INVALID_STATUS_TRANSITION  ("20001", "Invalid status transition for UserCredential", "error.credential.invalid_status_transition",   422),
    EMAIL_ALREADY_EXISTS       ("20002", "Email already exists",                         "error.credential.email_already_exists",         409),
    ACCOUNT_NOT_ACTIVE         ("20003", "Account is not active",                        "error.credential.account_not_active",           400),
    INVALID_PASSWORD           ("20004", "Invalid password",                             "error.credential.invalid_password",             400),
    PASSWORD_ALREADY_SET       ("20005", "Password has already been set",                "error.credential.password_already_set",         409),
    NOT_ALLOWED_FOR_CREDENTIAL_USER ("20006", "Operation not allowed for credential-based accounts", "error.credential.not_allowed_for_credential", 400),
    NO_PASSWORD_SET            ("20007", "No password has been set for this account",    "error.credential.no_password_set",              400);

    private final String code;
    private final String defaultMessage;
    private final String messageKey;
    private final int    httpStatus;

    UserCredentialErrorCode(String code, String defaultMessage, String messageKey, int httpStatus) {
        this.code           = code;
        this.defaultMessage = defaultMessage;
        this.messageKey     = messageKey;
        this.httpStatus     = httpStatus;
    }

    @Override public String code()           { return code; }
    @Override public String defaultMessage() { return defaultMessage; }
    @Override public String messageKey()     { return messageKey; }
    @Override public int    httpStatus()     { return httpStatus; }
}
