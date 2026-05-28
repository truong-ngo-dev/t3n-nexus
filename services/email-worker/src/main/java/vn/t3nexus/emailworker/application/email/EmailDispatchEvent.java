package vn.t3nexus.emailworker.application.email;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Debezium CDC event from notification_log, routed by SMT based on tier column.
 * Fields match notification_log column names (snake_case mapped via @JsonProperty).
 */
public record EmailDispatchEvent(
        String id,
        @JsonProperty("event_id") String eventId,
        @JsonProperty("notification_type") String notificationType,
        @JsonProperty("user_id") String userId,
        String recipient,
        String payload,
        @JsonProperty("created_at") Instant createdAt
) { }
