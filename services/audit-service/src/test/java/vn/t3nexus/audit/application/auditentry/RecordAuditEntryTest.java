package vn.t3nexus.audit.application.auditentry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import vn.t3nexus.audit.domain.auditentry.AuditEntry;
import vn.t3nexus.audit.domain.auditentry.AuditEntryRepository;
import vn.t3nexus.lib.audit.AuditActor;
import vn.t3nexus.lib.audit.AuditDetailsPolicy;
import vn.t3nexus.lib.audit.AuditRecord;
import vn.t3nexus.lib.audit.AuditTarget;
import vn.t3nexus.lib.common.domain.service.IdGenerator;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class RecordAuditEntryTest {

    private final AuditEntryRepository repository = mock(AuditEntryRepository.class);
    private final UUID fixedUuid = UUID.fromString("018f3a2c-4e89-7123-8abc-def012345678");
    private final IdGenerator idGenerator = () -> fixedUuid;
    private final Instant fixedNow = Instant.parse("2026-10-25T12:00:00.123456789Z");
    private final Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final RecordAuditEntry useCase = new RecordAuditEntry(repository, idGenerator, clock, meterRegistry);

    @Test
    void insertSucceedsReturnsTrueAndEntryHasGeneratedIdAndClockTime(CapturedOutput output) {
        when(repository.insertIfAbsent(any())).thenReturn(true);

        AuditRecord record = new AuditRecord(
                "ev-10", fixedNow, "trace-10", "order-service",
                new AuditActor(UUID.randomUUID(), "ADMIN"), "ORDER_APPROVED",
                new AuditTarget("ORDER", "ord-1"), Map.of("key", "val"), List.of()
        );

        RecordAuditEntry.Result result = useCase.handle(new RecordAuditEntry.Command(record));

        assertThat(result.recorded()).isTrue();

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(repository).insertIfAbsent(captor.capture());
        AuditEntry saved = captor.getValue();
        assertThat(saved.id()).isEqualTo(fixedUuid);
        assertThat(saved.recordedAt()).isEqualTo(fixedNow.truncatedTo(ChronoUnit.MICROS));
        assertThat(output.getAll()).contains("AUDIT_ENTRY_RECORDED").contains("entryId=" + fixedUuid);
        assertThat(output.getAll()).doesNotContain("val");
    }

    @Test
    void insertFailsReturnsFalse(CapturedOutput output) {
        when(repository.insertIfAbsent(any())).thenReturn(false);

        AuditRecord record = new AuditRecord(
                "ev-11", fixedNow, null, "srv",
                new AuditActor(null, "SYSTEM"), "CLEANUP_JOB",
                new AuditTarget("JOB", "j-1"), Map.of(), List.of()
        );

        RecordAuditEntry.Result result = useCase.handle(new RecordAuditEntry.Command(record));

        assertThat(result.recorded()).isFalse();
    }

    @Test
    void droppedDetailsAreLoggedWhenInsertedWithoutLeakingValues(CapturedOutput output) {
        when(repository.insertIfAbsent(any())).thenReturn(true);

        AuditDetailsPolicy.Dropped dropped = new AuditDetailsPolicy.Dropped("creditCardNumber", AuditDetailsPolicy.Reason.FORBIDDEN_NAME);
        AuditRecord record = new AuditRecord(
                "ev-12", fixedNow, null, "srv",
                new AuditActor(null, "SYSTEM"), "CARD_CHARGED",
                new AuditTarget("CARD", "c-1"), Map.of("safeKey", "safeVal"), List.of(dropped)
        );

        RecordAuditEntry.Result result = useCase.handle(new RecordAuditEntry.Command(record));

        assertThat(result.recorded()).isTrue();
        assertThat(output.getAll())
                .contains("AUDIT_DETAILS_KEY_DROPPED")
                .contains("creditCardNumber")
                .contains("FORBIDDEN_NAME")
                .doesNotContain("safeVal");
    }

    @Test
    void duplicateEventLogsIgnoredAndSkipsDroppedDetailsLog(CapturedOutput output) {
        when(repository.insertIfAbsent(any())).thenReturn(false);

        AuditDetailsPolicy.Dropped dropped = new AuditDetailsPolicy.Dropped("secretToken", AuditDetailsPolicy.Reason.FORBIDDEN_NAME);
        AuditRecord record = new AuditRecord(
                "ev-13", fixedNow, null, "srv",
                new AuditActor(null, "SYSTEM"), "TOKEN_REFRESHED",
                new AuditTarget("TOK", "t-1"), Map.of("reason", "expired"), List.of(dropped)
        );

        RecordAuditEntry.Result result = useCase.handle(new RecordAuditEntry.Command(record));

        assertThat(result.recorded()).isFalse();
        assertThat(output.getAll()).contains("AUDIT_DUPLICATE_IGNORED sourceEventId=ev-13");
        assertThat(output.getAll()).doesNotContain("AUDIT_DETAILS_KEY_DROPPED");
        assertThat(output.getAll()).doesNotContain("secretToken");
        assertThat(output.getAll()).doesNotContain("expired");
    }

    @Test
    void detailsContentIsNeverLogged(CapturedOutput output) {
        when(repository.insertIfAbsent(any())).thenReturn(true);

        AuditRecord record = new AuditRecord(
                "ev-14", fixedNow, null, "srv",
                new AuditActor(null, "SYSTEM"), "DATA_UPDATED",
                new AuditTarget("DATA", "d-1"), Map.of("secretDetail", "sensitiveContent999"), List.of()
        );

        useCase.handle(new RecordAuditEntry.Command(record));

        assertThat(output.getAll()).doesNotContain("sensitiveContent999");
        assertThat(output.getAll()).doesNotContain("secretDetail");
    }

    @Test
    void insertSucceedsIncrementsRecordedCounterAndDroppedKeysCounters() {
        // Bản ghi mới được chèn thành công làm tăng counter audit.entries.recorded và audit.details.keys_dropped theo từng lý do
        when(repository.insertIfAbsent(any())).thenReturn(true);

        AuditDetailsPolicy.Dropped dropped1 = new AuditDetailsPolicy.Dropped("token", AuditDetailsPolicy.Reason.FORBIDDEN_NAME);
        AuditDetailsPolicy.Dropped dropped2 = new AuditDetailsPolicy.Dropped("payload", AuditDetailsPolicy.Reason.TOO_LONG);

        AuditRecord record = new AuditRecord(
                "ev-metrics-1", fixedNow, "trace-m1", "order-service",
                new AuditActor(UUID.randomUUID(), "ADMIN"), "ORDER_CREATED",
                new AuditTarget("ORDER", "ord-m1"), Map.of("step", "1"), List.of(dropped1, dropped2)
        );

        RecordAuditEntry.Result result = useCase.handle(new RecordAuditEntry.Command(record));

        assertThat(result.recorded()).isTrue();

        double recordedCount = meterRegistry.counter("audit.entries.recorded", "sourceService", "order-service").count();
        double droppedForbidden = meterRegistry.counter("audit.details.keys_dropped", "reason", "FORBIDDEN_NAME").count();
        double droppedLarge = meterRegistry.counter("audit.details.keys_dropped", "reason", "TOO_LONG").count();
        double duplicateCount = meterRegistry.counter("audit.entries.duplicate").count();

        assertThat(recordedCount).isEqualTo(1.0);
        assertThat(droppedForbidden).isEqualTo(1.0);
        assertThat(droppedLarge).isEqualTo(1.0);
        assertThat(duplicateCount).isEqualTo(0.0);
    }

    @Test
    void duplicateEventIncrementsDuplicateCounterAndDoesNotIncrementRecordedOrDroppedCounters() {
        // Sự kiện trùng lặp làm tăng counter audit.entries.duplicate và không tăng các counter ghi nhận
        when(repository.insertIfAbsent(any())).thenReturn(false);

        AuditDetailsPolicy.Dropped dropped = new AuditDetailsPolicy.Dropped("secret", AuditDetailsPolicy.Reason.FORBIDDEN_NAME);

        AuditRecord record = new AuditRecord(
                "ev-metrics-2", fixedNow, "trace-m2", "payment-service",
                new AuditActor(UUID.randomUUID(), "SYSTEM"), "PAYMENT_PROCESSED",
                new AuditTarget("PAYMENT", "pay-1"), Map.of(), List.of(dropped)
        );

        RecordAuditEntry.Result result = useCase.handle(new RecordAuditEntry.Command(record));

        assertThat(result.recorded()).isFalse();

        double duplicateCount = meterRegistry.counter("audit.entries.duplicate").count();
        double recordedCount = meterRegistry.counter("audit.entries.recorded", "sourceService", "payment-service").count();
        double droppedCount = meterRegistry.counter("audit.details.keys_dropped", "reason", "FORBIDDEN_NAME").count();

        assertThat(duplicateCount).isEqualTo(1.0);
        assertThat(recordedCount).isEqualTo(0.0);
        assertThat(droppedCount).isEqualTo(0.0);
    }
}
