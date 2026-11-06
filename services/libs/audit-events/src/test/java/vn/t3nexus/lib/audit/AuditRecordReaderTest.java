package vn.t3nexus.lib.audit;

import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.events.EventEnvelope;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditRecordReaderTest {

    private Map<String, Object> createValidPayload(UUID accountId, String role) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sourceService", "seller-service");
        Map<String, Object> actor = new HashMap<>();
        actor.put("accountId", accountId != null ? accountId.toString() : null);
        actor.put("role", role);
        payload.put("actor", actor);
        payload.put("action", "SELLER_PROFILE_REJECTED");
        payload.put("target", Map.of("type", "SELLER_PROFILE", "id", "acc-123"));
        payload.put("details", Map.of("reason", "Incomplete documents"));
        return payload;
    }

    @Test
    void readsValidMinimalEnvelope() {
        UUID uid = UUID.randomUUID();
        Map<String, Object> payload = createValidPayload(uid, "ADMIN");

        EventEnvelope envelope = new EventEnvelope(
                "evt-100",
                "agg-wrong",
                "AggTypeWrong",
                AuditContract.EVENT_TYPE,
                Instant.parse("2026-10-24T10:15:30Z"),
                "trace-1",
                "span-1",
                1,
                payload
        );

        AuditRecord record = AuditRecordReader.read(envelope);

        assertThat(record.sourceEventId()).isEqualTo("evt-100");
        assertThat(record.occurredAt()).isEqualTo(Instant.parse("2026-10-24T10:15:30Z"));
        assertThat(record.traceId()).isEqualTo("trace-1");
        assertThat(record.sourceService()).isEqualTo("seller-service");
        assertThat(record.actor().role()).isEqualTo("ADMIN");
        assertThat(record.actor().accountId()).isEqualTo(uid);
        assertThat(record.action()).isEqualTo("SELLER_PROFILE_REJECTED");
        assertThat(record.target().type()).isEqualTo("SELLER_PROFILE");
        assertThat(record.target().id()).isEqualTo("acc-123");
        assertThat(record.details()).containsEntry("reason", "Incomplete documents");
        assertThat(record.droppedDetails()).isEmpty();
    }

    @Test
    void forbiddenKeyInDetailsIsFilteredToDroppedDetails() {
        UUID uid = UUID.randomUUID();
        Map<String, Object> payload = createValidPayload(uid, "ADMIN");
        Map<String, Object> details = new HashMap<>();
        details.put("reason", "fraud");
        details.put("email", "seller@example.com");
        payload.put("details", details);

        EventEnvelope envelope = new EventEnvelope(
                "evt-101",
                null,
                null,
                AuditContract.EVENT_TYPE,
                Instant.now(),
                null,
                null,
                1,
                payload
        );

        AuditRecord record = AuditRecordReader.read(envelope);

        assertThat(record.details()).containsOnlyKeys("reason");
        assertThat(record.droppedDetails()).hasSize(1);
        assertThat(record.droppedDetails().get(0).key()).isEqualTo("email");
        assertThat(record.droppedDetails().get(0).reason()).isEqualTo(AuditDetailsPolicy.Reason.FORBIDDEN_NAME);
    }

    @Test
    void systemRoleWithoutAccountIdIsValid() {
        Map<String, Object> payload = createValidPayload(null, "SYSTEM");

        EventEnvelope envelope = new EventEnvelope(
                "evt-102",
                null,
                null,
                AuditContract.EVENT_TYPE,
                Instant.now(),
                " ",
                null,
                1,
                payload
        );

        AuditRecord record = AuditRecordReader.read(envelope);

        assertThat(record.actor().role()).isEqualTo("SYSTEM");
        assertThat(record.actor().accountId()).isNull();
        assertThat(record.traceId()).isNull(); // Khoảng trắng thành null
    }

    @Test
    void missingEnvelopeThrowsWithCorrectField() {
        assertThatThrownBy(() -> AuditRecordReader.read(null))
                .isInstanceOf(InvalidAuditRecordException.class)
                .satisfies(e -> assertThat(((InvalidAuditRecordException) e).getField()).isEqualTo("envelope"));
    }

    @Test
    void missingEventIdThrowsWithCorrectField() {
        EventEnvelope envelope = new EventEnvelope(
                null, null, null, AuditContract.EVENT_TYPE, Instant.now(), null, null, 1, createValidPayload(UUID.randomUUID(), "ADMIN")
        );
        assertThatThrownBy(() -> AuditRecordReader.read(envelope))
                .isInstanceOf(InvalidAuditRecordException.class)
                .satisfies(e -> assertThat(((InvalidAuditRecordException) e).getField()).isEqualTo("eventId"));
    }

    @Test
    void wrongEventTypeThrowsWithCorrectField() {
        EventEnvelope envelope = new EventEnvelope(
                "e-1", null, null, "OtherEventType", Instant.now(), null, null, 1, createValidPayload(UUID.randomUUID(), "ADMIN")
        );
        assertThatThrownBy(() -> AuditRecordReader.read(envelope))
                .isInstanceOf(InvalidAuditRecordException.class)
                .satisfies(e -> assertThat(((InvalidAuditRecordException) e).getField()).isEqualTo("eventType"));
    }

    @Test
    void nullOccurredAtThrowsWithCorrectField() {
        EventEnvelope envelope = new EventEnvelope(
                "e-1", null, null, AuditContract.EVENT_TYPE, null, null, null, 1, createValidPayload(UUID.randomUUID(), "ADMIN")
        );
        assertThatThrownBy(() -> AuditRecordReader.read(envelope))
                .isInstanceOf(InvalidAuditRecordException.class)
                .satisfies(e -> assertThat(((InvalidAuditRecordException) e).getField()).isEqualTo("occurredAt"));
    }

    @Test
    void schemaVersionBelowOneThrowsWithCorrectField() {
        EventEnvelope envelope = new EventEnvelope(
                "e-1", null, null, AuditContract.EVENT_TYPE, Instant.now(), null, null, 0, createValidPayload(UUID.randomUUID(), "ADMIN")
        );
        assertThatThrownBy(() -> AuditRecordReader.read(envelope))
                .isInstanceOf(InvalidAuditRecordException.class)
                .satisfies(e -> assertThat(((InvalidAuditRecordException) e).getField()).isEqualTo("schemaVersion"));
    }

    @Test
    void nonMapPayloadThrowsWithCorrectField() {
        EventEnvelope envelope = new EventEnvelope(
                "e-1", null, null, AuditContract.EVENT_TYPE, Instant.now(), null, null, 1, "just a string"
        );
        assertThatThrownBy(() -> AuditRecordReader.read(envelope))
                .isInstanceOf(InvalidAuditRecordException.class)
                .satisfies(e -> assertThat(((InvalidAuditRecordException) e).getField()).isEqualTo("payload"));
    }

    @Test
    void invalidSourceServiceThrowsWithCorrectField() {
        Map<String, Object> payload = createValidPayload(UUID.randomUUID(), "ADMIN");
        payload.put("sourceService", "BAD_SERVICE");
        EventEnvelope envelope = new EventEnvelope(
                "e-1", null, null, AuditContract.EVENT_TYPE, Instant.now(), null, null, 1, payload
        );
        assertThatThrownBy(() -> AuditRecordReader.read(envelope))
                .isInstanceOf(InvalidAuditRecordException.class)
                .satisfies(e -> assertThat(((InvalidAuditRecordException) e).getField()).isEqualTo("sourceService"));
    }

    @Test
    void invalidActorThrowsWithCorrectField() {
        Map<String, Object> payload = createValidPayload(UUID.randomUUID(), "ADMIN");
        payload.put("actor", "not-a-map");
        EventEnvelope envelope = new EventEnvelope(
                "e-1", null, null, AuditContract.EVENT_TYPE, Instant.now(), null, null, 1, payload
        );
        assertThatThrownBy(() -> AuditRecordReader.read(envelope))
                .isInstanceOf(InvalidAuditRecordException.class)
                .satisfies(e -> assertThat(((InvalidAuditRecordException) e).getField()).isEqualTo("actor"));
    }

    @Test
    void invalidActorRoleThrowsWithCorrectField() {
        Map<String, Object> payload = createValidPayload(UUID.randomUUID(), "admin_lowercase");
        EventEnvelope envelope = new EventEnvelope(
                "e-1", null, null, AuditContract.EVENT_TYPE, Instant.now(), null, null, 1, payload
        );
        assertThatThrownBy(() -> AuditRecordReader.read(envelope))
                .isInstanceOf(InvalidAuditRecordException.class)
                .satisfies(e -> assertThat(((InvalidAuditRecordException) e).getField()).isEqualTo("actor.role"));
    }

    @Test
    void invalidActorAccountIdThrowsWithCorrectField() {
        Map<String, Object> payload = createValidPayload(UUID.randomUUID(), "ADMIN");
        @SuppressWarnings("unchecked")
        Map<String, Object> actor = (Map<String, Object>) payload.get("actor");
        actor.put("accountId", "not-a-uuid");

        EventEnvelope envelope = new EventEnvelope(
                "e-1", null, null, AuditContract.EVENT_TYPE, Instant.now(), null, null, 1, payload
        );
        assertThatThrownBy(() -> AuditRecordReader.read(envelope))
                .isInstanceOf(InvalidAuditRecordException.class)
                .satisfies(e -> assertThat(((InvalidAuditRecordException) e).getField()).isEqualTo("actor.accountId"));
    }

    @Test
    void invalidActionThrowsWithCorrectField() {
        Map<String, Object> payload = createValidPayload(UUID.randomUUID(), "ADMIN");
        payload.put("action", "invalid_action");
        EventEnvelope envelope = new EventEnvelope(
                "e-1", null, null, AuditContract.EVENT_TYPE, Instant.now(), null, null, 1, payload
        );
        assertThatThrownBy(() -> AuditRecordReader.read(envelope))
                .isInstanceOf(InvalidAuditRecordException.class)
                .satisfies(e -> assertThat(((InvalidAuditRecordException) e).getField()).isEqualTo("action"));
    }

    @Test
    void invalidTargetThrowsWithCorrectField() {
        Map<String, Object> payload = createValidPayload(UUID.randomUUID(), "ADMIN");
        payload.put("target", "not-a-map");
        EventEnvelope envelope = new EventEnvelope(
                "e-1", null, null, AuditContract.EVENT_TYPE, Instant.now(), null, null, 1, payload
        );
        assertThatThrownBy(() -> AuditRecordReader.read(envelope))
                .isInstanceOf(InvalidAuditRecordException.class)
                .satisfies(e -> assertThat(((InvalidAuditRecordException) e).getField()).isEqualTo("target"));
    }

    @Test
    void invalidTargetTypeThrowsWithCorrectField() {
        Map<String, Object> payload = createValidPayload(UUID.randomUUID(), "ADMIN");
        payload.put("target", Map.of("type", "invalid-type", "id", "123"));
        EventEnvelope envelope = new EventEnvelope(
                "e-1", null, null, AuditContract.EVENT_TYPE, Instant.now(), null, null, 1, payload
        );
        assertThatThrownBy(() -> AuditRecordReader.read(envelope))
                .isInstanceOf(InvalidAuditRecordException.class)
                .satisfies(e -> assertThat(((InvalidAuditRecordException) e).getField()).isEqualTo("target.type"));
    }

    @Test
    void invalidTargetIdThrowsWithCorrectField() {
        Map<String, Object> payload = createValidPayload(UUID.randomUUID(), "ADMIN");
        payload.put("target", Map.of("type", "VALID_TYPE", "id", "   "));
        EventEnvelope envelope = new EventEnvelope(
                "e-1", null, null, AuditContract.EVENT_TYPE, Instant.now(), null, null, 1, payload
        );
        assertThatThrownBy(() -> AuditRecordReader.read(envelope))
                .isInstanceOf(InvalidAuditRecordException.class)
                .satisfies(e -> assertThat(((InvalidAuditRecordException) e).getField()).isEqualTo("target.id"));
    }

    @Test
    void invalidDetailsTypeThrowsWithCorrectField() {
        Map<String, Object> payload = createValidPayload(UUID.randomUUID(), "ADMIN");
        payload.put("details", "not-a-map");
        EventEnvelope envelope = new EventEnvelope(
                "e-1", null, null, AuditContract.EVENT_TYPE, Instant.now(), null, null, 1, payload
        );
        assertThatThrownBy(() -> AuditRecordReader.read(envelope))
                .isInstanceOf(InvalidAuditRecordException.class)
                .satisfies(e -> assertThat(((InvalidAuditRecordException) e).getField()).isEqualTo("details"));
    }

    @Test
    void readerIgnoresEnvelopeAggregateIdAndType() {
        UUID uid = UUID.randomUUID();
        Map<String, Object> payload = createValidPayload(uid, "ADMIN");

        EventEnvelope envelope = new EventEnvelope(
                "evt-100",
                "diff-agg-id",
                "DIFF_AGG_TYPE",
                AuditContract.EVENT_TYPE,
                Instant.now(),
                null,
                null,
                1,
                payload
        );

        AuditRecord record = AuditRecordReader.read(envelope);
        assertThat(record.target().type()).isEqualTo("SELLER_PROFILE");
        assertThat(record.target().id()).isEqualTo("acc-123");
    }

    @Test
    void rejectionMessageNeverContainsSecretValue() {
        Map<String, Object> payload = createValidPayload(UUID.randomUUID(), "ADMIN");
        payload.put("sourceService", "BAD VALUE SECRET");

        EventEnvelope envelope = new EventEnvelope(
                "evt-1", null, null, AuditContract.EVENT_TYPE, Instant.now(), null, null, 1, payload
        );

        assertThatThrownBy(() -> AuditRecordReader.read(envelope))
                .isInstanceOf(InvalidAuditRecordException.class)
                .satisfies(e -> {
                    assertThat(e.getMessage()).isEqualTo("Bản ghi kiểm toán không hợp lệ: sourceService");
                    assertThat(e.getMessage()).doesNotContain("SECRET");
                });
    }
}
