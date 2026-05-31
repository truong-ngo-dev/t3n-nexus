package vn.t3nexus.identity.domain.user_account;

import vn.t3nexus.lib.common.domain.exception.DomainException;

public class EmailVerificationException extends DomainException {

    public EmailVerificationException(EmailVerificationErrorCode errorCode) {
        super(errorCode);
    }

    public static EmailVerificationException notFound() {
        return new EmailVerificationException(EmailVerificationErrorCode.VERIFICATION_NOT_FOUND);
    }

    public static EmailVerificationException expired() {
        return new EmailVerificationException(EmailVerificationErrorCode.VERIFICATION_EXPIRED);
    }

    public static EmailVerificationException invalid() {
        return new EmailVerificationException(EmailVerificationErrorCode.VERIFICATION_INVALID);
    }

    public static EmailVerificationException alreadyVerified() {
        return new EmailVerificationException(EmailVerificationErrorCode.ALREADY_VERIFIED);
    }
}
