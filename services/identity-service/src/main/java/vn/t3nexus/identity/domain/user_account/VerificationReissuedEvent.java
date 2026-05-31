package vn.t3nexus.identity.domain.user_account;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;
import vn.t3nexus.lib.common.domain.model.DomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class VerificationReissuedEvent extends AbstractDomainEvent implements DomainEvent {

    private final String userId;
    private final String email;
    private final String fullName;
    private final String verificationToken;

    public VerificationReissuedEvent(String verificationId, String userId, String email, String fullName, String verificationToken) {
        super(UUID.randomUUID().toString(), Instant.now(), verificationId, EmailVerification.class.getSimpleName());
        this.userId            = userId;
        this.email             = email;
        this.fullName          = fullName;
        this.verificationToken = verificationToken;
    }

    public String getUserId()            { return userId; }
    public String getEmail()             { return email; }
    public String getFullName()          { return fullName; }
    public String getVerificationToken() { return verificationToken; }

    @Override
    public String getRoutingKey() {
        return "identity.email-verification.reissued";
    }

    @Override
    public Object getPayload() {
        return Map.of(
                "userId",            userId,
                "email",             email,
                "fullName",          fullName,
                "verificationToken", verificationToken
        );
    }
}
