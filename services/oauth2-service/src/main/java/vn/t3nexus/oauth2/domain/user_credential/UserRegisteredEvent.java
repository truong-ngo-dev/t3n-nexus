package vn.t3nexus.oauth2.domain.user_credential;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;
import vn.t3nexus.lib.common.domain.model.DomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class UserRegisteredEvent extends AbstractDomainEvent implements DomainEvent {

    private final String userId;
    private final String email;
    private final String fullName;
    private final String role;
    private final String registrationMethod;

    public UserRegisteredEvent(String userId, String email, String fullName, String role, String registrationMethod) {
        super(UUID.randomUUID().toString(), Instant.now(), userId, "UserCredential");
        this.userId             = userId;
        this.email              = email;
        this.fullName           = fullName;
        this.role               = role;
        this.registrationMethod = registrationMethod;
    }

    public String getUserId()             { return userId; }
    public String getEmail()              { return email; }
    public String getFullName()           { return fullName; }
    public String getRole()               { return role; }
    public String getRegistrationMethod() { return registrationMethod; }

    @Override
    public String getRoutingKey() {
        return "oauth2.user.registered";
    }

    @Override
    public Object getPayload() {
        return Map.of(
                "userId",             userId,
                "email",              email,
                "fullName",           fullName,
                "role",               role,
                "registrationMethod", registrationMethod
        );
    }
}
