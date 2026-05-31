package vn.t3nexus.identity.domain.user_account;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;
import vn.t3nexus.lib.common.domain.model.DomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class CustomerAccountCreatedEvent extends AbstractDomainEvent implements DomainEvent {

    private final String userId;
    private final String email;
    private final String fullName;

    public CustomerAccountCreatedEvent(String userId, String email, String fullName) {
        super(UUID.randomUUID().toString(), Instant.now(), userId, "UserAccount");
        this.userId   = userId;
        this.email    = email;
        this.fullName = fullName;
    }

    public String getUserId()   { return userId; }
    public String getEmail()    { return email; }
    public String getFullName() { return fullName; }

    @Override
    public String getRoutingKey() {
        return "identity.customer-account.created";
    }

    @Override
    public Object getPayload() {
        return Map.of(
                "userId",   userId,
                "email",    email,
                "fullName", fullName
        );
    }
}
