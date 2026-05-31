package vn.t3nexus.identity.infrastructure.messaging;

public record UserRegisteredPayload(
        String email,
        String fullName,
        String role,
        String registrationMethod
) {}
