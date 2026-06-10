package vn.t3nexus.identity.infrastructure.messaging;

public record LoginFailedPayload(
        String userId,
        String loginIdentifier,
        String result,
        String deviceHash,
        String acceptLanguage,
        String ipAddress,
        String userAgent,
        String provider
) {}
