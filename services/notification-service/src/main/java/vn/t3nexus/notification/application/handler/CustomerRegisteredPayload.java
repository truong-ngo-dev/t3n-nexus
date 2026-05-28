package vn.t3nexus.notification.application.handler;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirrors CustomerRegisteredEvent from identity-service.
 * aggregateId carries userId (set by identity-service's AbstractDomainEvent).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerRegisteredPayload(
        String aggregateId,
        String email,
        String fullName,
        String registrationMethod,
        String verificationToken
) {}
