package vn.t3nexus.emailworker.application.email;

import java.util.Map;

/**
 * Deserialized from notification_log.payload JSONB.
 * Contains everything email-worker needs — no additional DB query required.
 */
public record EmailPayload(
        String title,
        String body,
        Map<String, Object> attributes
) {}
