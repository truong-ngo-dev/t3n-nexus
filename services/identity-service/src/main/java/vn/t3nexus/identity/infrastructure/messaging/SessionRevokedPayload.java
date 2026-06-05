package vn.t3nexus.identity.infrastructure.messaging;

public record SessionRevokedPayload(
        String authorizationId,
        String oauthSessionId,
        String userId,
        String idpSessionId
) {}
