package vn.t3nexus.audit.domain.auditentry;

import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.audit.AuditActor;
import vn.t3nexus.lib.audit.AuditTarget;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditEntryRestoreTest {

    @Test
    void restorePreservesAllFieldsWithoutTruncation() {
        // Dữ liệu đọc từ kho lưu trữ cần được giữ nguyên toàn bộ trường, kể cả độ chính xác thời gian
        UUID id = UUID.randomUUID();
        String sourceEventId = "ev-restore-1";
        Instant occurredAt = Instant.parse("2026-10-25T10:15:30.123456789Z");
        Instant recordedAt = Instant.parse("2026-10-25T10:15:31.987654321Z");
        AuditActor actor = new AuditActor(UUID.randomUUID(), "ADMIN");
        String action = "ORDER_COMPLETED";
        AuditTarget target = new AuditTarget("ORDER", "order-123");
        Map<String, Object> details = Map.of("reason", "manual", "amount", 1000);
        String sourceService = "order-service";
        String traceId = "trace-restore-999";

        AuditEntry entry = AuditEntry.restore(
                id,
                sourceEventId,
                occurredAt,
                recordedAt,
                actor,
                action,
                target,
                details,
                sourceService,
                traceId
        );

        assertThat(entry.id()).isEqualTo(id);
        assertThat(entry.sourceEventId()).isEqualTo(sourceEventId);
        assertThat(entry.occurredAt()).isEqualTo(occurredAt);
        assertThat(entry.recordedAt()).isEqualTo(recordedAt);
        assertThat(entry.actor()).isEqualTo(actor);
        assertThat(entry.action()).isEqualTo(action);
        assertThat(entry.target()).isEqualTo(target);
        assertThat(entry.details()).containsExactlyInAnyOrderEntriesOf(details);
        assertThat(entry.sourceService()).isEqualTo(sourceService);
        assertThat(entry.traceId()).isEqualTo(traceId);
    }

    @Test
    void detailsMapIsUnmodifiable() {
        // Bản ghi kiểm toán là bất biến nên trường details không được phép sửa đổi
        Map<String, Object> mutableMap = new HashMap<>();
        mutableMap.put("k", "v");

        AuditEntry entry = AuditEntry.restore(
                UUID.randomUUID(),
                "ev-restore-2",
                Instant.now(),
                Instant.now(),
                new AuditActor(UUID.randomUUID(), "USER"),
                "USER_LOGIN",
                new AuditTarget("USER", "u-1"),
                mutableMap,
                "auth-service",
                null
        );

        assertThatThrownBy(() -> entry.details().put("new_key", "val"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullMandatoryParametersAreRejected() {
        // Các tham số bắt buộc không được phép null
        UUID id = UUID.randomUUID();
        String sourceEventId = "ev-restore-3";
        Instant occurredAt = Instant.now();
        Instant recordedAt = Instant.now();
        AuditActor actor = new AuditActor(UUID.randomUUID(), "USER");
        String action = "USER_LOGIN";
        AuditTarget target = new AuditTarget("USER", "u-1");
        Map<String, Object> details = Map.of();
        String sourceService = "auth-service";
        String traceId = "trace-1";

        assertThatThrownBy(() -> AuditEntry.restore(null, sourceEventId, occurredAt, recordedAt, actor, action, target, details, sourceService, traceId))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> AuditEntry.restore(id, null, occurredAt, recordedAt, actor, action, target, details, sourceService, traceId))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> AuditEntry.restore(id, sourceEventId, null, recordedAt, actor, action, target, details, sourceService, traceId))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> AuditEntry.restore(id, sourceEventId, occurredAt, null, actor, action, target, details, sourceService, traceId))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> AuditEntry.restore(id, sourceEventId, occurredAt, recordedAt, null, action, target, details, sourceService, traceId))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> AuditEntry.restore(id, sourceEventId, occurredAt, recordedAt, actor, null, target, details, sourceService, traceId))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> AuditEntry.restore(id, sourceEventId, occurredAt, recordedAt, actor, action, null, details, sourceService, traceId))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> AuditEntry.restore(id, sourceEventId, occurredAt, recordedAt, actor, action, target, details, null, traceId))
                .isInstanceOf(NullPointerException.class);
    }
}
