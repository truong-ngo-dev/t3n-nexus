package vn.t3nexus.identity.domain.user_account;

import vn.t3nexus.lib.common.domain.exception.DomainException;

public class UserAccountException extends DomainException {

    public UserAccountException(UserAccountErrorCode errorCode) {
        super(errorCode);
    }

    public static UserAccountException notFound() {
        return new UserAccountException(UserAccountErrorCode.USER_NOT_FOUND);
    }

    public static UserAccountException locked() {
        return new UserAccountException(UserAccountErrorCode.ACCOUNT_LOCKED);
    }

    public static UserAccountException invalidStatusTransition() {
        return new UserAccountException(UserAccountErrorCode.INVALID_STATUS_TRANSITION);
    }

    public static UserAccountException userIsNotLocked() {
        return new UserAccountException(UserAccountErrorCode.USER_NOT_LOCKED);
    }

    public static UserAccountException userIsNotActive() {
        return new UserAccountException(UserAccountErrorCode.USER_NOT_ACTIVE);
    }

    public static UserAccountException resendNotAllowed() {
        return new UserAccountException(UserAccountErrorCode.RESEND_NOT_ALLOWED);
    }

    public static UserAccountException rateLimitExceeded() {
        return new UserAccountException(UserAccountErrorCode.RATE_LIMIT_EXCEEDED);
    }
}
