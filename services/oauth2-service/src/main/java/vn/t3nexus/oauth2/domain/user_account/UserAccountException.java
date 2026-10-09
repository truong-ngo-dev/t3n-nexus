package vn.t3nexus.oauth2.domain.user_account;

import vn.t3nexus.lib.common.domain.exception.DomainException;

public class UserAccountException extends DomainException {

    private UserAccountException(UserAccountErrorCode code, String message) {
        super(code, message);
    }

    public static UserAccountException invalidStatusTransition() {
        return new UserAccountException(UserAccountErrorCode.INVALID_STATUS_TRANSITION,
                "Invalid status transition for UserAccount");
    }

    public static UserAccountException emailTaken() {
        return new UserAccountException(UserAccountErrorCode.EMAIL_TAKEN,
                UserAccountErrorCode.EMAIL_TAKEN.defaultMessage());
    }

    public static UserAccountException emailInvalid() {
        return new UserAccountException(UserAccountErrorCode.EMAIL_INVALID,
                UserAccountErrorCode.EMAIL_INVALID.defaultMessage());
    }

    public static UserAccountException emailReserved() {
        return new UserAccountException(UserAccountErrorCode.EMAIL_RESERVED,
                UserAccountErrorCode.EMAIL_RESERVED.defaultMessage());
    }

    public static UserAccountException passwordInvalid() {
        return new UserAccountException(UserAccountErrorCode.PASSWORD_INVALID,
                UserAccountErrorCode.PASSWORD_INVALID.defaultMessage());
    }

    public static UserAccountException accountNotActive() {
        return new UserAccountException(UserAccountErrorCode.ACCOUNT_NOT_ACTIVE,
                "Account is not active");
    }

    public static UserAccountException wrongPassword() {
        return new UserAccountException(UserAccountErrorCode.WRONG_PASSWORD,
                UserAccountErrorCode.WRONG_PASSWORD.defaultMessage());
    }

    public static UserAccountException passwordAlreadySet() {
        return new UserAccountException(UserAccountErrorCode.PASSWORD_ALREADY_SET,
                "Password has already been set for this account");
    }

    public static UserAccountException notAllowedForCredentialUser() {
        return new UserAccountException(UserAccountErrorCode.NOT_ALLOWED_FOR_CREDENTIAL_USER,
                "Operation not allowed for credential-based accounts");
    }

    public static UserAccountException noPasswordSet() {
        return new UserAccountException(UserAccountErrorCode.NO_PASSWORD_SET,
                "No password has been set for this account");
    }

    public static UserAccountException notFound() {
        return new UserAccountException(UserAccountErrorCode.CREDENTIAL_NOT_FOUND,
                "Credential not found");
    }

    public static UserAccountException setupTokenInvalid() {
        return new UserAccountException(UserAccountErrorCode.SETUP_TOKEN_INVALID,
                "Password setup token is invalid or expired");
    }

    public static UserAccountException setupRateLimited() {
        return new UserAccountException(UserAccountErrorCode.SETUP_RATE_LIMITED,
                "Please wait before requesting another link");
    }
}
