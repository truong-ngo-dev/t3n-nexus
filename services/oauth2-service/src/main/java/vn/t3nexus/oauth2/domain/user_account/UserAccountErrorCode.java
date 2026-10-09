package vn.t3nexus.oauth2.domain.user_account;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

public enum UserAccountErrorCode implements ErrorCode {

    INVALID_STATUS_TRANSITION  ("20001", "Invalid status transition for UserAccount", "error.credential.invalid_status_transition",   422),
    EMAIL_TAKEN                ("20002", "Email này đã được dùng để đăng ký",            "error.credential.email_taken",                  409),
    ACCOUNT_NOT_ACTIVE         ("20003", "Account is not active",                        "error.credential.account_not_active",           400),
    WRONG_PASSWORD             ("20004", "Mật khẩu không đúng",                          "error.credential.wrong_password",               400),
    PASSWORD_ALREADY_SET       ("20005", "Password has already been set",                "error.credential.password_already_set",         409),
    NOT_ALLOWED_FOR_CREDENTIAL_USER ("20006", "Operation not allowed for credential-based accounts", "error.credential.not_allowed_for_credential", 400),
    NO_PASSWORD_SET            ("20007", "No password has been set for this account",    "error.credential.no_password_set",              400),
    CREDENTIAL_NOT_FOUND       ("20008", "Credential not found",                         "error.credential.not_found",                    404),
    SETUP_TOKEN_INVALID        ("20009", "Password setup token is invalid or expired",    "error.credential.setup_token_invalid",           400),
    SETUP_RATE_LIMITED         ("20010", "Please wait before requesting another link",    "error.credential.setup_rate_limited",            429),
    EMAIL_INVALID              ("20011", "Email không đúng định dạng hoặc dài quá 254 ký tự", "error.credential.email_invalid",         400),
    EMAIL_RESERVED             ("20012", "Email thuộc miền nội bộ, không dùng để đăng ký công khai", "error.credential.email_reserved", 400),
    PASSWORD_INVALID           ("20013", "Mật khẩu phải từ 8 đến 64 ký tự, chỉ gồm chữ cái không dấu, chữ số và ký tự @ hoặc $, có ít nhất một chữ cái và một chữ số", "error.credential.password_invalid", 400);

    private final String code;
    private final String defaultMessage;
    private final String messageKey;
    private final int    httpStatus;

    UserAccountErrorCode(String code, String defaultMessage, String messageKey, int httpStatus) {
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
