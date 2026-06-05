package vn.t3nexus.oauth2.domain.session;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

public enum OAuthSessionErrorCode implements ErrorCode {

    SESSION_NOT_FOUND          ("21001", "Session not found",                                               "error.session.not_found",              404),
    SESSION_NOT_ACTIVE         ("21002", "Session is not active",                                           "error.session.not_active",             409),
    SESSION_ALREADY_REVOKED    ("21003", "Session is already revoked",                                      "error.session.already_revoked",        409),
    SESSION_EXPIRED            ("21004", "Session has expired",                                             "error.session.expired",                401),
    SESSION_NOT_BELONG_TO_USER ("21005", "Session does not belong to user",                                 "error.session.not_belong_to_user",     403),
    CANNOT_REVOKE_CURRENT      ("21006", "Cannot revoke your current session. Use standard logout instead", "error.session.cannot_revoke_current",  400);

    private final String code;
    private final String defaultMessage;
    private final String messageKey;
    private final int    httpStatus;

    OAuthSessionErrorCode(String code, String defaultMessage, String messageKey, int httpStatus) {
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
