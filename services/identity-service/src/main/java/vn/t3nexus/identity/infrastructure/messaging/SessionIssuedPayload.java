package vn.t3nexus.identity.infrastructure.messaging;

public record SessionIssuedPayload(
        String authorizationId,
        String oauthSessionId,
        String idpSessionId,
        String userId,
        String loginIdentifier,
        String deviceHash,
        String userAgent,
        String acceptLanguage,
        String ipAddress,
        String provider
) {}
