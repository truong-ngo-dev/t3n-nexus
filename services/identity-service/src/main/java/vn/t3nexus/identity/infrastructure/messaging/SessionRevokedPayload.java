package vn.t3nexus.identity.infrastructure.messaging;

import java.util.List;

public record SessionRevokedPayload(
        List<String> oauthSessionIds,
        String       userId,
        String       idpSessionId
) {}
