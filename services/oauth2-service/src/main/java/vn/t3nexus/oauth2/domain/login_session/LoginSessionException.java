package vn.t3nexus.oauth2.domain.login_session;

import vn.t3nexus.lib.common.domain.exception.DomainException;

public class LoginSessionException extends DomainException {

    private LoginSessionException(LoginSessionErrorCode code) {
        super(code, code.defaultMessage());
    }

    /** Phiên người dùng đã quá hạn hoặc không còn. */
    public static LoginSessionException sessionExpired() {
        return new LoginSessionException(LoginSessionErrorCode.SESSION_EXPIRED);
    }

    /** Hai yêu cầu cùng cấp phiên ứng dụng cho một (phiên người dùng, client): bên thua ràng buộc duy nhất thử lại (INV-ATH-10). */
    public static LoginSessionException clientSessionConflict() {
        return new LoginSessionException(LoginSessionErrorCode.CLIENT_SESSION_CONFLICT);
    }

    /** Mã phiên ứng dụng không thuộc phiên người dùng này (ví dụ {@code sid} không khớp khi làm mới). */
    public static LoginSessionException clientSessionMismatch() {
        return new LoginSessionException(LoginSessionErrorCode.CLIENT_SESSION_MISMATCH);
    }
}
