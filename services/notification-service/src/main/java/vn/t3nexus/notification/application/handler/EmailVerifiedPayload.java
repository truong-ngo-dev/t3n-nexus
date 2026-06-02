package vn.t3nexus.notification.application.handler;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirrors EmailVerified from identity-service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EmailVerifiedPayload(
        String userId,
        String email,
        String fullName
) {}
