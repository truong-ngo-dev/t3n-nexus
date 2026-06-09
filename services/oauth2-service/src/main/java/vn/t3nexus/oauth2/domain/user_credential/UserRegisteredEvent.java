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
    private final String setupToken;

    public UserRegisteredEvent(String userId, String email, String fullName, String role, String registrationMethod) {
        this(userId, email, fullName, role, registrationMethod, null);
    }

    public UserRegisteredEvent(String userId, String email, String fullName, String role, String registrationMethod, String setupToken) {
        super(UUID.randomUUID().toString(), Instant.now(), userId, "UserCredential");
        this.userId             = userId;
        this.email              = email;
        this.fullName           = fullName;
        this.role               = role;
        this.registrationMethod = registrationMethod;
        this.setupToken         = setupToken;
    }

    public String getUserId()             { return userId; }
    public String getEmail()              { return email; }
    public String getFullName()           { return fullName; }
    public String getRole()               { return role; }
    public String getRegistrationMethod() { return registrationMethod; }
    public String getSetupToken()         { return setupToken; }

    @Override
    public String getRoutingKey() {
        return "oauth2.user.registered";
    }

    @Override
    public Object getPayload() {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("email",              email);
        payload.put("fullName",           fullName);
        payload.put("role",               role);
        payload.put("registrationMethod", registrationMethod);
        if (setupToken != null) payload.put("setupToken", setupToken);
        return payload;
    }
}
