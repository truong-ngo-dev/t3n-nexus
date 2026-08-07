package vn.t3nexus.identity.domain.user_account;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;
import vn.t3nexus.lib.common.domain.model.DomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class UserActivatedEvent extends AbstractDomainEvent implements DomainEvent {

    private final String userId;

    public UserActivatedEvent(String userId) {
        super(UUID.randomUUID().toString(), Instant.now(), userId, UserAccount.class.getSimpleName());
        this.userId = userId;
    }

    public String getUserId() { return userId; }

    @Override
    public String getRoutingKey() {
        return "identity.user.activated";
    }

    @Override
    public Object getPayload() {
        return Map.of("userId", userId);
    }
}
