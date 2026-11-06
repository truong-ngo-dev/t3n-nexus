package vn.t3nexus.audit.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.t3nexus.audit.AuditPostgresTestBase;
import vn.t3nexus.audit.AuditServiceApplication;
import vn.t3nexus.audit.application.auditentry.RecordAuditEntry;
import vn.t3nexus.audit.domain.auditentry.AuditEntry;
import vn.t3nexus.lib.audit.AuditActor;
import vn.t3nexus.lib.audit.AuditRecord;
import vn.t3nexus.lib.audit.AuditRecordReader;
import vn.t3nexus.lib.audit.AuditTarget;
import vn.t3nexus.lib.events.EventEnvelope;
import vn.t3nexus.lib.eventconsumer.testing.DeliveryHarness;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AuditServiceApplication.class, properties = {
        "spring.main.web-application-type=none",
        "spring.kafka.listener.auto-startup=false"
})
class AuditEntryRepositoryAdapterIntegrationTest extends AuditPostgresTestBase {

    @Autowired
    private AuditEntryRepositoryAdapter repository;

    @Autowired
    private RecordAuditEntry recordAuditEntry;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void insertValidRecordSucceedsAndReadsBackCorrectly() {
        String eventId = "ev-" + UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        Instant occurredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant recordedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        UUID actorId = UUID.randomUUID();

        AuditRecord record = new AuditRecord(
                eventId, occurredAt, "trace-kt01", "seller-service",
                new AuditActor(actorId, "ADMIN"), "SELLER_VERIFIED",
                new AuditTarget("SELLER", "seller-99"),
                Map.of("step", 2, "comment", "ok"), List.of()
        );

        AuditEntry entry = AuditEntry.record(entryId, recordedAt, record);
        boolean inserted = repository.insertIfAbsent(entry);

        assertThat(inserted).isTrue();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT id, source_event_id, occurred_at, recorded_at, actor_account_id, actor_role, action, target_type, target_id, details::text AS details, source_service, trace_id FROM audit_entries WHERE source_event_id = ?",
                eventId
        );

        assertThat(row.get("id")).isEqualTo(entryId);
        assertThat(row.get("source_event_id")).isEqualTo(eventId);
        assertThat(((Timestamp) row.get("occurred_at")).toInstant()).isEqualTo(occurredAt);
        assertThat(((Timestamp) row.get("recorded_at")).toInstant()).isEqualTo(recordedAt);
        assertThat(row.get("actor_account_id")).isEqualTo(actorId);
        assertThat(row.get("actor_role")).isEqualTo("ADMIN");
        assertThat(row.get("action")).isEqualTo("SELLER_VERIFIED");
        assertThat(row.get("target_type")).isEqualTo("SELLER");
        assertThat(row.get("target_id")).isEqualTo("seller-99");
        assertThat(row.get("source_service")).isEqualTo("seller-service");
        assertThat(row.get("trace_id")).isEqualTo("trace-kt01");
        // Postgres chuẩn hóa jsonb (thêm dấu cách), nên so theo nội dung JSON, không theo chuỗi
        var details = tools.jackson.databind.json.JsonMapper.builder().build().readTree((String) row.get("details"));
        assertThat(details.get("step").asInt()).isEqualTo(2);
        assertThat(details.get("comment").asText()).isEqualTo("ok");
        assertThat(details.size()).isEqualTo(2);
    }

    @Test
    void systemActorWithoutAccountIdStoresNullAndEmptyDetailsStoresEmptyJson() {
        String eventId = "ev-" + UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        AuditRecord record = new AuditRecord(
                eventId, Instant.now(), null, "batch-service",
                new AuditActor(null, "SYSTEM"), "CACHE_PURGED",
                new AuditTarget("CACHE", "cache-all"), Map.of(), List.of()
        );

        AuditEntry entry = AuditEntry.record(entryId, Instant.now(), record);
        boolean inserted = repository.insertIfAbsent(entry);

        assertThat(inserted).isTrue();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT actor_account_id, details::text AS details FROM audit_entries WHERE source_event_id = ?",
                eventId
        );

        assertThat(row.get("actor_account_id")).isNull();
        assertThat((String) row.get("details")).isEqualTo("{}");
    }

    @Test
    void snapshotActorRoleAndAccountIdArePreservedWithoutExternalLookup() {
        String eventId = "ev-" + UUID.randomUUID();
        UUID customActorId = UUID.randomUUID();
        AuditRecord record = new AuditRecord(
                eventId, Instant.now(), null, "auth-service",
                new AuditActor(customActorId, "ADMIN"), "PERMISSION_GRANTED",
                new AuditTarget("ROLE", "role-1"), Map.of(), List.of()
        );

        AuditEntry entry = AuditEntry.record(UUID.randomUUID(), Instant.now(), record);
        boolean inserted = repository.insertIfAbsent(entry);

        assertThat(inserted).isTrue();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT actor_account_id, actor_role FROM audit_entries WHERE source_event_id = ?",
                eventId
        );

        assertThat(row.get("actor_account_id")).isEqualTo(customActorId);
        assertThat(row.get("actor_role")).isEqualTo("ADMIN");
    }

    @Test
    void duplicateSourceEventIdReturnsFalseAndConcurrentInsertsResultInSingleRow() throws Exception {
        String eventId = "ev-" + UUID.randomUUID();
        AuditRecord record = new AuditRecord(
                eventId, Instant.now(), null, "order-service",
                new AuditActor(UUID.randomUUID(), "BUYER"), "ORDER_PLACED",
                new AuditTarget("ORDER", "ord-1"), Map.of(), List.of()
        );

        boolean first = repository.insertIfAbsent(AuditEntry.record(UUID.randomUUID(), Instant.now(), record));
        boolean second = repository.insertIfAbsent(AuditEntry.record(UUID.randomUUID(), Instant.now(), record));

        assertThat(first).isTrue();
        assertThat(second).isFalse();

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_entries WHERE source_event_id = ?",
                Integer.class,
                eventId
        );
        assertThat(count).isEqualTo(1);

        String concurrentEventId = "ev-concurrent-" + UUID.randomUUID();
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit((Callable<Boolean>) () -> {
                startLatch.await();
                AuditRecord rec = new AuditRecord(
                        concurrentEventId, Instant.now(), null, "order-service",
                        new AuditActor(UUID.randomUUID(), "BUYER"), "ORDER_PLACED",
                        new AuditTarget("ORDER", "ord-1"), Map.of(), List.of()
                );
                return repository.insertIfAbsent(AuditEntry.record(UUID.randomUUID(), Instant.now(), rec));
            }));
        }

        startLatch.countDown();
        pool.shutdown();

        int successCount = 0;
        int failureCount = 0;
        for (Future<Boolean> f : futures) {
            if (f.get()) {
                successCount++;
            } else {
                failureCount++;
            }
        }

        assertThat(successCount).isEqualTo(1);
        assertThat(failureCount).isEqualTo(19);

        Integer concurrentRowCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_entries WHERE source_event_id = ?",
                Integer.class,
                concurrentEventId
        );
        assertThat(concurrentRowCount).isEqualTo(1);
    }

    @Test
    void deliveryHarnessVerifiesDuplicateDeliveryIsHarmless() {
        String eventId = "ev-" + UUID.randomUUID();
        EventEnvelope envelope = new EventEnvelope(
                eventId,
                "target-1",
                "TARGET",
                "AuditActionRecorded",
                Instant.now(),
                "trace-harness",
                null,
                1,
                Map.of(
                        "sourceService", "catalog-service",
                        "actor", Map.of("role", "ADMIN", "accountId", UUID.randomUUID().toString()),
                        "action", "CATALOG_UPDATED",
                        "target", Map.of("type", "CATALOG", "id", "c-1"),
                        "details", Map.of()
                )
        );

        DeliveryHarness<Integer> harness = new DeliveryHarness<>(
                env -> recordAuditEntry.handle(new RecordAuditEntry.Command(AuditRecordReader.read(env))),
                () -> jdbcTemplate.queryForObject("SELECT count(*) FROM audit_entries WHERE source_event_id = ?", Integer.class, eventId),
                () -> {}
        );

        harness.assertDuplicateDeliveryIsHarmless(envelope);
    }

    @Test
    void unfamiliarActionSavesSuccessfullyViaUseCase() {
        String eventId = "ev-" + UUID.randomUUID();
        AuditRecord record = new AuditRecord(
                eventId, Instant.now(), null, "seller-service",
                new AuditActor(UUID.randomUUID(), "ADMIN"), "WIDGET_FROBNICATED_CUSTOM_ACTION",
                new AuditTarget("WIDGET", "w-1"), Map.of(), List.of()
        );

        RecordAuditEntry.Result result = recordAuditEntry.handle(new RecordAuditEntry.Command(record));

        assertThat(result.recorded()).isTrue();
        String savedAction = jdbcTemplate.queryForObject(
                "SELECT action FROM audit_entries WHERE source_event_id = ?",
                String.class,
                eventId
        );
        assertThat(savedAction).isEqualTo("WIDGET_FROBNICATED_CUSTOM_ACTION");
    }
}
