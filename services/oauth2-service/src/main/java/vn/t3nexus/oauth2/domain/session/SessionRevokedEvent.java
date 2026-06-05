package vn.t3nexus.oauth2.domain.session;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;
import vn.t3nexus.lib.common.domain.model.DomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class SessionRevokedEvent extends AbstractDomainEvent implements DomainEvent {

    private final String oauthSessionId;
    private final String userId;
    private final String idpSessionId;

    public SessionRevokedEvent(String authorizationId, String oauthSessionId, String userId, String idpSessionId) {
        super(UUID.randomUUID().toString(), Instant.now(), oauthSessionId, "OAuthSession");
        this.oauthSessionId = oauthSessionId;
        this.userId         = userId;
        this.idpSessionId   = idpSessionId;
    }

    public String getOauthSessionId() { return oauthSessionId; }
    public String getUserId()         { return userId; }
    public String getIdpSessionId()   { return idpSessionId; }

    @Override
    public String getRoutingKey() {
        return "oauth2.session.revoked";
    }

    @Override
    public Object getPayload() {
        return Map.of(
                "authorizationId", getAggregateId(),
                "oauthSessionId",  oauthSessionId,
                "userId",          userId,
                "idpSessionId",    idpSessionId
        );
    }
}
