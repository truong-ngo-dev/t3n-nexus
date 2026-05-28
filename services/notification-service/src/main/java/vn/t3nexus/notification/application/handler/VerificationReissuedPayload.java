package vn.t3nexus.notification.application.handler;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirrors VerificationReissuedEvent from identity-service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VerificationReissuedPayload(
        String userId,
        String email,
        String fullName,
        String verificationToken
) {}
