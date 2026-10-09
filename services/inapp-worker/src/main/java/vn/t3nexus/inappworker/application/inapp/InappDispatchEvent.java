package vn.t3nexus.inappworker.application.inapp;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Debezium CDC event từ {@code notification_log}, route bởi SMT theo cột {@code channel=IN_APP}.
 * Field khớp tên cột {@code notification_log} (snake_case qua {@code @JsonProperty}) — cùng shape với
 * {@code email-worker.EmailDispatchEvent} (cùng 1 bảng nguồn), chỉ khác {@code recipient} luôn null
 * (chỉ có ý nghĩa với channel=EMAIL).
 */
public record InappDispatchEvent(
        String id,
        @JsonProperty("event_id") String eventId,
        @JsonProperty("notification_type") String notificationType,
        @JsonProperty("user_id") String userId,
        String payload,
        @JsonProperty("created_at") Instant createdAt
) { }
