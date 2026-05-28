package vn.t3nexus.identity.domain.user;

import vn.t3nexus.lib.common.domain.exception.DomainException;

public class UserException extends DomainException {

    public UserException(UserErrorCode errorCode) {
        super(errorCode);
    }

    public static UserException notFound() {
        return new UserException(UserErrorCode.USER_NOT_FOUND);
    }

    public static UserException emailAlreadyExists() {
        return new UserException(UserErrorCode.EMAIL_ALREADY_EXISTS);
    }

    public static UserException locked() {
        return new UserException(UserErrorCode.ACCOUNT_LOCKED);
    }

    public static UserException currentPasswordRequired() {
        return new UserException(UserErrorCode.CURRENT_PASSWORD_REQUIRED);
    }

    public static UserException invalidPassword() {
        return new UserException(UserErrorCode.INVALID_PASSWORD);
    }

    public static UserException invalidStatusTransition() {
        return new UserException(UserErrorCode.INVALID_STATUS_TRANSITION);
    }

    public static UserException userIsNotLocked() {
        return new UserException(UserErrorCode.USER_NOT_LOCKED);
    }

    public static UserException userIsNotActive() {
        return new UserException(UserErrorCode.USER_NOT_ACTIVE);
    }

    public static UserException resendNotAllowed() {
        return new UserException(UserErrorCode.RESEND_NOT_ALLOWED);
    }

    public static UserException rateLimitExceeded() {
        return new UserException(UserErrorCode.RATE_LIMIT_EXCEEDED);
    }
}
