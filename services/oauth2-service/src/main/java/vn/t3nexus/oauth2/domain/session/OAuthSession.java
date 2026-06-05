package vn.t3nexus.oauth2.domain.session;

import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;
import vn.t3nexus.lib.common.domain.vo.UserId;

import java.time.Instant;

public class OAuthSession extends AbstractAggregateRoot<OAuthSessionId> implements AggregateRoot<OAuthSessionId> {

    private final UserId  userId;
    private final String  idpSessionId;
    private final String  authorizationId;
    private final String  ipAddress;
    private       SessionStatus status;
    private final Instant createdAt;

    private OAuthSession(OAuthSessionId id, UserId userId, String idpSessionId,
                         String authorizationId, String ipAddress) {
        setId(id);
        this.userId          = userId;
        this.idpSessionId    = idpSessionId;
        this.authorizationId = authorizationId;
        this.ipAddress       = ipAddress;
        this.status          = SessionStatus.ACTIVE;
        this.createdAt       = Instant.now();
    }

    private OAuthSession(OAuthSessionId id, UserId userId, String idpSessionId,
                         String authorizationId, String ipAddress,
                         SessionStatus status, Instant createdAt) {
        setId(id);
        this.userId          = userId;
        this.idpSessionId    = idpSessionId;
        this.authorizationId = authorizationId;
        this.ipAddress       = ipAddress;
        this.status          = status;
        this.createdAt       = createdAt;
    }

    public static OAuthSession issue(OAuthSessionId id, UserId userId, String idpSessionId,
                                     String authorizationId, String ipAddress,
                                     SessionIssuanceContext context) {
        OAuthSession session = new OAuthSession(id, userId, idpSessionId, authorizationId, ipAddress);
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
                                            SessionStatus status, Instant createdAt) {
        return new OAuthSession(id, userId, idpSessionId, authorizationId, ipAddress, status, createdAt);
    }

    public void revoke() {
        assertActive();
        this.status = SessionStatus.REVOKED;
        addDomainEvent(new SessionRevokedEvent(
                authorizationId, getId().getValueAsString(), userId.getValueAsString(), idpSessionId));
    }

    public void expire() {
        assertActive();
        this.status = SessionStatus.EXPIRED;
    }

    public boolean isActive() {
        return status == SessionStatus.ACTIVE;
    }

    public boolean belongsTo(UserId userId) {
        return this.userId.equals(userId);
    }

    public UserId        getUserId()          { return userId; }
    public String        getIdpSessionId()    { return idpSessionId; }
    public String        getAuthorizationId() { return authorizationId; }
    public String        getIpAddress()       { return ipAddress; }
    public SessionStatus getStatus()          { return status; }
    public Instant       getCreatedAt()       { return createdAt; }

    private void assertActive() {
        if (status != SessionStatus.ACTIVE) {
            throw new DomainException(OAuthSessionErrorCode.SESSION_NOT_ACTIVE);
        }
    }
}
