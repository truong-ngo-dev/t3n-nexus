package vn.t3nexus.identity.infrastructure.messaging;

import java.util.List;

public record SessionExpiredBulkPayload(List<String> oauthSessionIds) {}
