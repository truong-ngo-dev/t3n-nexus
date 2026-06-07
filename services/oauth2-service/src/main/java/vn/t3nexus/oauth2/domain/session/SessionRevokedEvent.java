package vn.t3nexus.oauth2.domain.session;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;
import vn.t3nexus.lib.common.domain.model.DomainEvent;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SessionRevokedEvent extends AbstractDomainEvent implements DomainEvent {

    private final List<String> oauthSessionIds;
    private final String       userId;
    private final String       idpSessionId;

    public SessionRevokedEvent(String idpSessionId, List<String> oauthSessionIds, String userId) {
        super(UUID.randomUUID().toString(), Instant.now(), idpSessionId, "OAuthSession");
        this.oauthSessionIds = List.copyOf(oauthSessionIds);
        this.userId          = userId;
        this.idpSessionId    = idpSessionId;
    }

    public List<String> getOauthSessionIds() { return oauthSessionIds; }
    public String       getUserId()          { return userId; }
    public String       getIdpSessionId()    { return idpSessionId; }

    @Override
    public String getRoutingKey() {
        return "oauth2.session.revoked";
    }

    @Override
    public Object getPayload() {
        return Map.of(
                "oauthSessionIds", oauthSessionIds,
                "userId",          userId,
                "idpSessionId",    idpSessionId
        );
    }
}
