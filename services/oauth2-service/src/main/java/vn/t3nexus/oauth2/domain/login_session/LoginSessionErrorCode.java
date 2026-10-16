package vn.t3nexus.oauth2.domain.login_session;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

public enum LoginSessionErrorCode implements ErrorCode {
    SESSION_EXPIRED         ("21001", "Phiên đăng nhập đã hết hạn",                 "error.session.expired",                 401),
    CLIENT_SESSION_MISMATCH ("21002", "Phiên ứng dụng không thuộc phiên đăng nhập này", "error.session.client_session_mismatch", 400),
    CLIENT_SESSION_CONFLICT ("21003", "Phiên ứng dụng vừa được cấp bởi yêu cầu khác, hãy thử lại", "error.session.client_session_conflict", 409);

    private final String code;
    private final String defaultMessage;
    private final String messageKey;
    private final int    httpStatus;

    LoginSessionErrorCode(String code, String defaultMessage, String messageKey, int httpStatus) {
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
