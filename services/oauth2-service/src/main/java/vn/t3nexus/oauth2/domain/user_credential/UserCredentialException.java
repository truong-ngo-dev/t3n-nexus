package vn.t3nexus.oauth2.domain.user_credential;

import vn.t3nexus.lib.common.domain.exception.DomainException;

public class UserCredentialException extends DomainException {

    private UserCredentialException(UserCredentialErrorCode code, String message) {
        super(code, message);
    }

    public static UserCredentialException invalidStatusTransition() {
        return new UserCredentialException(UserCredentialErrorCode.INVALID_STATUS_TRANSITION,
                "Invalid status transition for UserCredential");
    }

    public static UserCredentialException emailAlreadyExists() {
        return new UserCredentialException(UserCredentialErrorCode.EMAIL_ALREADY_EXISTS,
                "Email already exists");
    }

    public static UserCredentialException accountNotActive() {
        return new UserCredentialException(UserCredentialErrorCode.ACCOUNT_NOT_ACTIVE,
                "Account is not active");
    }

    public static UserCredentialException invalidPassword() {
        return new UserCredentialException(UserCredentialErrorCode.INVALID_PASSWORD,
                "Invalid password");
    }

    public static UserCredentialException passwordAlreadySet() {
        return new UserCredentialException(UserCredentialErrorCode.PASSWORD_ALREADY_SET,
                "Password has already been set for this account");
    }

    public static UserCredentialException notAllowedForCredentialUser() {
        return new UserCredentialException(UserCredentialErrorCode.NOT_ALLOWED_FOR_CREDENTIAL_USER,
                "Operation not allowed for credential-based accounts");
    }

    public static UserCredentialException noPasswordSet() {
        return new UserCredentialException(UserCredentialErrorCode.NO_PASSWORD_SET,
                "No password has been set for this account");
    }
}
