package vn.t3nexus.identity.domain.user;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;
import vn.t3nexus.lib.common.domain.model.DomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class CustomerRegisteredEvent extends AbstractDomainEvent implements DomainEvent {

    private final String email;
    private final String fullName;
    private final RegistrationMethod registrationMethod;
    private final String verificationToken;

    public CustomerRegisteredEvent(String userId, String email, String fullName, RegistrationMethod registrationMethod, String verificationToken) {
        super(UUID.randomUUID().toString(), Instant.now(), userId, User.class.getSimpleName());
        this.email = email;
        this.fullName = fullName;
        this.registrationMethod = registrationMethod;
        this.verificationToken = verificationToken;
    }

    public enum RegistrationMethod {
        CREDENTIAL,
        OAUTH
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public RegistrationMethod getRegistrationMethod() {
        return registrationMethod;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    @Override
    public String getRoutingKey() {
        return "identity.user.registered";
    }

    @Override
    public Object getPayload() {
        return Map.of(
                "aggregateId",        getAggregateId(),
                "email",              email,
                "fullName",           fullName,
                "registrationMethod", registrationMethod.name(),
                "verificationToken",  verificationToken
        );
    }
}
