package vn.t3nexus.oauth2.domain.user_credential;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;
import vn.t3nexus.lib.common.domain.model.DomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class PasswordSetupResentEvent extends AbstractDomainEvent implements DomainEvent {

    private final String userId;
    private final String email;
    private final String setupToken;

    public PasswordSetupResentEvent(String userId, String email, String setupToken) {
        super(UUID.randomUUID().toString(), Instant.now(), userId, "UserCredential");
        this.userId     = userId;
        this.email      = email;
        this.setupToken = setupToken;
    }

    public String getUserId()     { return userId; }
    public String getEmail()      { return email; }
    public String getSetupToken() { return setupToken; }

    @Override
    public String getRoutingKey() {
        return "hoahóa";
    }

    @Override
    public Object getPayload() {
        return Map.of(
                "userId",     userId,
                "email",      email,
                "setupToken", setupToken
        );
    }
}
