package vn.t3nexus.audit.domain.auditentry;

import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.audit.AuditActor;
import vn.t3nexus.lib.audit.AuditDetailsPolicy;
import vn.t3nexus.lib.audit.AuditRecord;
import vn.t3nexus.lib.audit.AuditTarget;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditEntryTest {

    @Test
    void recordBuildsAllFieldsProperly() {
        UUID id = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-10-25T10:15:30.123456789Z");
        Instant recordedAt = Instant.parse("2026-10-25T10:15:35.987654321Z");
        AuditActor actor = new AuditActor(UUID.randomUUID(), "ADMIN");
        AuditTarget target = new AuditTarget("ORDER", "ord-123");
        Map<String, Object> details = Map.of("reason", "manual_check");
        AuditRecord record = new AuditRecord(
                "ev-1", occurredAt, "trace-1", "order-service",
                actor, "ORDER_CANCELLED", target, details, List.of()
        );

        AuditEntry entry = AuditEntry.record(id, recordedAt, record);

        assertThat(entry.id()).isEqualTo(id);
        assertThat(entry.sourceEventId()).isEqualTo("ev-1");
        assertThat(entry.occurredAt()).isEqualTo(Instant.parse("2026-10-25T10:15:30.123456Z"));
        assertThat(entry.recordedAt()).isEqualTo(Instant.parse("2026-10-25T10:15:35.987654Z"));
        assertThat(entry.actor()).isEqualTo(actor);
        assertThat(entry.action()).isEqualTo("ORDER_CANCELLED");
        assertThat(entry.target()).isEqualTo(target);
        assertThat(entry.details()).containsExactlyEntriesOf(details);
        assertThat(entry.sourceService()).isEqualTo("order-service");
        assertThat(entry.traceId()).isEqualTo("trace-1");
    }

    @Test
    void timestampsAreTruncatedToMicros() {
        UUID id = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-10-25T10:00:00.123456789Z");
        Instant recordedAt = Instant.parse("2026-10-25T10:00:00.987654321Z");
        AuditRecord record = new AuditRecord(
                "ev-2", occurredAt, null, "srv",
                new AuditActor(null, "SYSTEM"), "ACTION_EXECUTED",
                new AuditTarget("SYS", "sys-1"), Map.of(), List.of()
        );

        AuditEntry entry = AuditEntry.record(id, recordedAt, record);

        assertThat(entry.occurredAt().getNano()).isEqualTo(123456000);
        assertThat(entry.recordedAt().getNano()).isEqualTo(987654000);
    }

    @Test
    void detailsIsUnmodifiableAndImmuneToSourceMapMutations() {
        UUID id = UUID.randomUUID();
        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("k1", "v1");

        AuditRecord record = new AuditRecord(
                "ev-3", Instant.now(), null, "srv",
                new AuditActor(null, "SYSTEM"), "ACTION_EXECUTED",
                new AuditTarget("SYS", "sys-1"), sourceMap, List.of()
        );

        AuditEntry entry = AuditEntry.record(id, Instant.now(), record);

        sourceMap.put("k2", "v2");
        assertThat(entry.details()).doesNotContainKey("k2");

        assertThatThrownBy(() -> entry.details().put("k3", "v3"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullArgumentsAreRejected() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        AuditRecord record = new AuditRecord(
                "ev-4", now, null, "srv",
                new AuditActor(null, "SYSTEM"), "ACTION_EXECUTED",
                new AuditTarget("SYS", "sys-1"), Map.of(), List.of()
        );

        assertThatThrownBy(() -> AuditEntry.record(null, now, record))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("id");

        assertThatThrownBy(() -> AuditEntry.record(id, null, record))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("recordedAt");

        assertThatThrownBy(() -> AuditEntry.record(id, now, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("record");
    }

    @Test
    void systemActorWithoutAccountIdIsPreserved() {
        UUID id = UUID.randomUUID();
        AuditActor actor = new AuditActor(null, "SYSTEM");
        AuditRecord record = new AuditRecord(
                "ev-5", Instant.now(), null, "srv",
                actor, "BATCH_CLEANUP",
                new AuditTarget("SYS", "job-1"), Map.of(), List.of()
        );

        AuditEntry entry = AuditEntry.record(id, Instant.now(), record);

        assertThat(entry.actor().accountId()).isNull();
        assertThat(entry.actor().role()).isEqualTo("SYSTEM");
    }
}
