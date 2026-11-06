package vn.t3nexus.lib.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditActionRecordedTest {

    @Test
    void gettersAndRoutingKeyAndSimpleName() {
        UUID uid = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-24T12:00:00Z");
        AuditActor actor = new AuditActor(uid, "ADMIN");
        AuditTarget target = new AuditTarget("USER_ACCOUNT", "u-1");
        Map<String, Object> details = Map.of("reason", "manual review");

        AuditActionRecorded event = new AuditActionRecorded(
                "evt-123",
                now,
                "admin-service",
                actor,
                "USER_SUSPENDED",
                target,
                details
        );

        assertThat(event.getClass().getSimpleName()).isEqualTo("AuditActionRecorded");
        assertThat(event.getEventId()).isEqualTo("evt-123");
        assertThat(event.getOccurredOn()).isEqualTo(now);
        assertThat(event.getAggregateType()).isEqualTo("USER_ACCOUNT");
        assertThat(event.getAggregateId()).isEqualTo("u-1");
        assertThat(event.getRoutingKey()).isEqualTo(AuditContract.TOPIC);
    }

    @Test
    void payloadStructureMatchesContract() {
        UUID uid = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-24T12:00:00Z");
        AuditActor actor = new AuditActor(uid, "SELLER");
        AuditTarget target = new AuditTarget("PRODUCT", "p-99");
        Map<String, Object> details = Map.of("step", "published");

        AuditActionRecorded event = new AuditActionRecorded(
                "evt-1",
                now,
                "product-service",
                actor,
                "PRODUCT_PUBLISHED",
                target,
                details
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) event.getPayload();

        assertThat(payload).containsOnlyKeys("sourceService", "actor", "action", "target", "details");
        assertThat(payload.get("sourceService")).isEqualTo("product-service");
        assertThat(payload.get("action")).isEqualTo("PRODUCT_PUBLISHED");
        assertThat(payload.get("actor")).isEqualTo(Map.of("accountId", uid.toString(), "role", "SELLER"));
        assertThat(payload.get("target")).isEqualTo(Map.of("type", "PRODUCT", "id", "p-99"));
        assertThat(payload.get("details")).isEqualTo(details);
    }

    @Test
    void payloadWithSystemActorHasNullAccountIdAndNullDetailsBecomesEmpty() {
        AuditActor actor = new AuditActor(null, "SYSTEM");
        AuditTarget target = new AuditTarget("CRON_JOB", "job-1");

        AuditActionRecorded event = new AuditActionRecorded(
                "evt-2",
                Instant.now(),
                "batch-service",
                actor,
                "JOB_COMPLETED",
                target,
                null
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) event.getPayload();

        @SuppressWarnings("unchecked")
        Map<String, Object> actorMap = (Map<String, Object>) payload.get("actor");
        assertThat(actorMap.get("accountId")).isNull();
        assertThat(actorMap.get("role")).isEqualTo("SYSTEM");

        @SuppressWarnings("unchecked")
        Map<String, Object> detailsMap = (Map<String, Object>) payload.get("details");
        assertThat(detailsMap).isEmpty();
    }

    @Test
    void blankEventIdIsRejected() {
        AuditActor actor = new AuditActor(null, "SYSTEM");
        AuditTarget target = new AuditTarget("SYSTEM_CONFIG", "cfg-1");

        assertThatThrownBy(() -> new AuditActionRecorded(
                "   ",
                Instant.now(),
                "config-service",
                actor,
                "CONFIG_RELOADED",
                target,
                Map.of()
        )).isInstanceOf(IllegalArgumentException.class).hasMessage("eventId");
    }
}
