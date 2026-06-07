package vn.t3nexus.oauth2.domain.session;

import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;
import vn.t3nexus.lib.common.domain.vo.UserId;

import java.time.Instant;
import java.util.List;

public class OAuthSession extends AbstractAggregateRoot<OAuthSessionId> implements AggregateRoot<OAuthSessionId> {

    private final UserId        userId;
    private final String        idpSessionId;
    private       String        authorizationId;
    private final String        ipAddress;
    private final String        registeredClientId;
    private       SessionStatus status;
    private final Instant       createdAt;

    private OAuthSession(OAuthSessionId id, UserId userId, String idpSessionId,
                         String authorizationId, String ipAddress, String registeredClientId) {
        setId(id);
        this.userId             = userId;
        this.idpSessionId       = idpSessionId;
        this.authorizationId    = authorizationId;
        this.ipAddress          = ipAddress;
        this.registeredClientId = registeredClientId;
        this.status             = SessionStatus.ACTIVE;
        this.createdAt          = Instant.now();
    }

    private OAuthSession(OAuthSessionId id, UserId userId, String idpSessionId,
                         String authorizationId, String ipAddress, String registeredClientId,
                         SessionStatus status, Instant createdAt) {
        setId(id);
        this.userId             = userId;
        this.idpSessionId       = idpSessionId;
        this.authorizationId    = authorizationId;
        this.ipAddress          = ipAddress;
        this.registeredClientId = registeredClientId;
        this.status             = status;
        this.createdAt          = createdAt;
    }

    public static OAuthSession issue(OAuthSessionId id, UserId userId, String idpSessionId,
                                     String authorizationId, String ipAddress,
                                     String registeredClientId,
                                     SessionIssuanceContext context) {
        OAuthSession session = new OAuthSession(id, userId, idpSessionId, authorizationId, ipAddress, registeredClientId);
        session.addDomainEvent(new SessionIssuedEvent(
                authorizationId,
                id.getValueAsString(),
                idpSessionId,
                userId.getValueAsString(),
                context.loginIdentifier(),
                context.deviceHash(),
                context.userAgent(),
                context.acceptLanguage(),
                ipAddress,
                context.provider()
        ));
        return session;
    }

    public static OAuthSession reconstitute(OAuthSessionId id, UserId userId, String idpSessionId,
                                            String authorizationId, String ipAddress,
                                            String registeredClientId, Instant createdAt) {
        return new OAuthSession(id, userId, idpSessionId, authorizationId, ipAddress,
                registeredClientId, SessionStatus.ACTIVE, createdAt);
    }

    public void revoke() {
        assertActive();
        this.status = SessionStatus.REVOKED;
        addDomainEvent(new SessionRevokedEvent(
                idpSessionId, List.of(getId().getValueAsString()), userId.getValueAsString()));
    }

    public void onTokenRotated(String newAuthorizationId) {
        this.authorizationId = newAuthorizationId;
    }

    public boolean isActive() {
        return status == SessionStatus.ACTIVE;
    }

    public boolean belongsTo(UserId userId) {
        return this.userId.equals(userId);
    }

    public UserId        getUserId()             { return userId; }
    public String        getIdpSessionId()       { return idpSessionId; }
    public String        getAuthorizationId()    { return authorizationId; }
    public String        getIpAddress()          { return ipAddress; }
    public String        getRegisteredClientId() { return registeredClientId; }
    public SessionStatus getStatus()             { return status; }
    public Instant       getCreatedAt()          { return createdAt; }

    private void assertActive() {
        if (status != SessionStatus.ACTIVE) {
            throw new DomainException(OAuthSessionErrorCode.SESSION_NOT_ACTIVE);
        }
    }
}
