package vn.t3nexus.oauth2.domain.user_credential;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;
import vn.t3nexus.lib.common.domain.model.DomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class LoginOtpRequestedEvent extends AbstractDomainEvent implements DomainEvent {

    private final String email;
    private final String token;

    public LoginOtpRequestedEvent(String userId, String email, String token) {
        super(UUID.randomUUID().toString(), Instant.now(), userId, "UserCredential");
        this.email = email;
        this.token = token;
    }

    public String getEmail() { return email; }
    public String getToken() { return token; }

    @Override
    public String getRoutingKey() {
        return "oauth2.login-otp.requested";
    }

    @Override
    public Object getPayload() {
        return Map.of(
                "email", email,
                "token", token
        );
    }
}
