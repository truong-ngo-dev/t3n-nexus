package vn.t3nexus.oauth2.domain.session;

import vn.t3nexus.lib.common.domain.exception.DomainException;

public class OAuthSessionException extends DomainException {

    private OAuthSessionException(OAuthSessionErrorCode code) {
        super(code);
    }

    public static OAuthSessionException notFound()            { return new OAuthSessionException(OAuthSessionErrorCode.SESSION_NOT_FOUND); }
    public static OAuthSessionException notBelongToUser()     { return new OAuthSessionException(OAuthSessionErrorCode.SESSION_NOT_BELONG_TO_USER); }
    public static OAuthSessionException cannotRevokeCurrent() { return new OAuthSessionException(OAuthSessionErrorCode.CANNOT_REVOKE_CURRENT); }
}
