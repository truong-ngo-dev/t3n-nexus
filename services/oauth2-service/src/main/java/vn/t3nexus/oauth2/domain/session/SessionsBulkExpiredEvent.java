package vn.t3nexus.oauth2.domain.session;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;
import vn.t3nexus.lib.common.domain.model.DomainEvent;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SessionsBulkExpiredEvent extends AbstractDomainEvent implements DomainEvent {

    private final List<String> oauthSessionIds;

    public SessionsBulkExpiredEvent(List<String> oauthSessionIds) {
        super(UUID.randomUUID().toString(), Instant.now(), "bulk", "OAuthSession");
        this.oauthSessionIds = List.copyOf(oauthSessionIds);
    }

    public List<String> getOauthSessionIds() { return oauthSessionIds; }

    @Override
    public String getRoutingKey() {
        return "oauth2.session.expired.bulk";
    }

    @Override
    public Object getPayload() {
        return Map.of("oauthSessionIds", oauthSessionIds);
    }
}
