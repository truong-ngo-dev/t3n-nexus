package vn.t3nexus.lib.outbox;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.lib.common.domain.model.DomainEvent;
import vn.t3nexus.lib.events.SensitiveFieldException;
import vn.t3nexus.outboxtest.TestApplication;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KT-01, KT-02, KT-03 trên Postgres thật (Testcontainers, không chạm cơ sở dữ liệu dev). Bảng dựng từ chính mẫu
 * migration của thư viện và Hibernate chạy {@code validate}, nên test cũng chứng minh mẫu khớp entity.
 */
@SpringBootTest(classes = TestApplication.class, properties = {
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/outbox-schema.sql",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class OutboxEventStoreIntegrationTest {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired OutboxEventStore store;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        jdbc.execute("TRUNCATE TABLE outbox_events");
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private int rows() {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class);
    }

    // ---- KT-01 ----

    @Test
    void rollbackLeavesNoRow() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            store.store(new TestEvent("e-1", "agg-1", "probe.thing.created", Map.of("n", 1)));
            throw new IllegalStateException("lỗi sau khi đã ghi hộp thư");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(rows()).isZero();
    }

    @Test
    void markedRollbackOnlyLeavesNoRow() {
        tx.executeWithoutResult(status -> {
            store.store(new TestEvent("e-1", "agg-1", "probe.thing.created", Map.of("n", 1)));
            status.setRollbackOnly();
        });

        assertThat(rows()).isZero();
    }

    // ---- KT-02 ----

    @Test
    void commitLeavesOneRowPerEventWithAllColumns() throws Exception {
        MDC.put("traceId", "trace-xyz");
        MDC.put("spanId", "span-xyz");
        Instant occurred = Instant.parse("2026-10-24T01:02:03Z");

        tx.executeWithoutResult(status -> {
            store.store(new TestEvent("e-1", "agg-1", "probe.thing.created", Map.of("n", 1), occurred));
            store.store(new TestEvent("e-2", "agg-1", "probe.thing.updated", Map.of("n", 2), occurred));
        });

        assertThat(rows()).isEqualTo(2);
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM outbox_events WHERE event_id = 'e-1'");
        assertThat(row.get("aggregate_type")).isEqualTo("Probe");
        assertThat(row.get("aggregate_id")).isEqualTo("agg-1");
        assertThat(row.get("event_type")).isEqualTo("TestEvent");
        assertThat(row.get("routing_key")).isEqualTo("probe.thing.created");
        assertThat(row.get("trace_id")).isEqualTo("trace-xyz");
        assertThat(row.get("span_id")).isEqualTo("span-xyz");
        assertThat(row.get("occurred_on")).isNotNull();
        assertThat(row.get("created_at")).isNotNull();

        JsonNode envelope = objectMapper.readTree((String) row.get("payload"));
        assertThat(envelope.get("eventId").asString()).isEqualTo("e-1");
        assertThat(envelope.get("aggregateId").asString()).isEqualTo("agg-1");
        assertThat(envelope.get("aggregateType").asString()).isEqualTo("Probe");
        assertThat(envelope.get("eventType").asString()).isEqualTo("TestEvent");
        assertThat(envelope.get("occurredAt").asString()).isEqualTo("2026-10-24T01:02:03Z");
        assertThat(envelope.get("traceId").asString()).isEqualTo("trace-xyz");
        assertThat(envelope.get("spanId").asString()).isEqualTo("span-xyz");
        assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("payload").get("n").asInt()).isEqualTo(1);
        assertThat(envelope.has("schema")).isFalse();
    }

    @Test
    void secondWriteWithTheSameEventIdIsRejectedByUniqueConstraint() {
        tx.executeWithoutResult(status ->
                store.store(new TestEvent("e-dup", "agg-1", "probe.thing.created", Map.of("n", 1))));

        assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                store.store(new TestEvent("e-dup", "agg-1", "probe.thing.created", Map.of("n", 1)))))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(rows()).isEqualTo(1);
    }

    // ---- KT-03 ----

    @Test
    void writeOutsideATransactionIsRejected() {
        assertThatThrownBy(() -> store.store(new TestEvent("e-1", "agg-1", "probe.thing.created", Map.of("n", 1))))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(rows()).isZero();
    }

    // ---- KT-11 ở cổng ghi: chính sách mặc định là nghiêm ----

    @Test
    void sensitiveFieldInPayloadIsRejectedAndNothingIsWritten() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                store.store(new TestEvent("e-1", "agg-1", "probe.thing.created", Map.of("seller", Map.of("taxCode", "0123456789"))))))
                .isInstanceOf(SensitiveFieldException.class)
                .hasMessageContaining("payload.seller.taxCode");

        assertThat(rows()).isZero();
    }

    static class TestEvent implements DomainEvent {
        private final String eventId;
        private final String aggregateId;
        private final String routingKey;
        private final Object payload;
        private final Instant occurredOn;

        TestEvent(String eventId, String aggregateId, String routingKey, Object payload) {
            this(eventId, aggregateId, routingKey, payload, Instant.now());
        }

        TestEvent(String eventId, String aggregateId, String routingKey, Object payload, Instant occurredOn) {
            this.eventId = eventId;
            this.aggregateId = aggregateId;
            this.routingKey = routingKey;
            this.payload = payload;
            this.occurredOn = occurredOn;
        }

        @Override public String getEventId() { return eventId; }
        @Override public Instant getOccurredOn() { return occurredOn; }
        @Override public String getAggregateId() { return aggregateId; }
        @Override public String getAggregateType() { return "Probe"; }
        @Override public String getRoutingKey() { return routingKey; }
        @Override public Object getPayload() { return payload; }
    }
}
