package vn.t3nexus.lib.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.auditeventstest.TestApplication;
import vn.t3nexus.lib.events.EventEnvelope;
import vn.t3nexus.lib.events.EventEnvelopeReader;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = TestApplication.class, properties = {
        "spring.application.name=seller-service",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/outbox-schema.sql",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(AuditRecorderIntegrationTest.TestCatalogConfig.class)
class AuditRecorderIntegrationTest {

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

    @TestConfiguration
    static class TestCatalogConfig {
        @Bean
        AuditActionCatalog auditActionCatalog() {
            return AuditActionCatalog.of(
                    new AuditActionDeclaration("SELLER_PROFILE_REJECTED", Set.of("profileVersion", "reason")),
                    new AuditActionDeclaration("CATEGORY_CREATED", Set.of())
            );
        }
    }

    @Autowired AuditRecorder recorder;
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

    @Test
    void commitLeavesOneRowAndContractRoundTripSucceeds() {
        UUID actorId = UUID.randomUUID();
        AuditActor actor = new AuditActor(actorId, "ADMIN");
        AuditTarget target = new AuditTarget("SELLER_PROFILE", "acc-555");
        Map<String, Object> details = Map.of("profileVersion", 2, "reason", "Tài liệu mờ");

        tx.executeWithoutResult(status ->
                recorder.record("SELLER_PROFILE_REJECTED", actor, target, details)
        );

        assertThat(rows()).isEqualTo(1);
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM outbox_events");

        assertThat(row.get("routing_key")).isEqualTo("audit.action.recorded");
        assertThat(row.get("aggregate_type")).isEqualTo("SELLER_PROFILE");
        assertThat(row.get("aggregate_id")).isEqualTo("acc-555");
        assertThat(row.get("event_type")).isEqualTo("AuditActionRecorded");

        String payloadJson = (String) row.get("payload");
        EventEnvelopeReader envelopeReader = new EventEnvelopeReader(objectMapper);
        EventEnvelope envelope = envelopeReader.read(payloadJson);

        AuditRecord auditRecord = AuditRecordReader.read(envelope);
        assertThat(auditRecord.sourceEventId()).isEqualTo((String) row.get("event_id"));
        assertThat(auditRecord.actor().accountId()).isEqualTo(actorId);
        assertThat(auditRecord.actor().role()).isEqualTo("ADMIN");
        assertThat(auditRecord.action()).isEqualTo("SELLER_PROFILE_REJECTED");
        assertThat(auditRecord.target().type()).isEqualTo("SELLER_PROFILE");
        assertThat(auditRecord.target().id()).isEqualTo("acc-555");
        assertThat(auditRecord.details()).containsEntry("profileVersion", 2).containsEntry("reason", "Tài liệu mờ");
        assertThat(auditRecord.sourceService()).isEqualTo("seller-service");
    }

    @Test
    void rollbackLeavesZeroRows() {
        AuditActor actor = new AuditActor(null, "SYSTEM");
        AuditTarget target = new AuditTarget("CATEGORY", "cat-1");

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            recorder.record("CATEGORY_CREATED", actor, target, Map.of());
            throw new RuntimeException("Lỗi sau khi ghi kiểm toán");
        })).isInstanceOf(RuntimeException.class);

        assertThat(rows()).isZero();
    }

    @Test
    void writeOutsideTransactionThrowsIllegalTransactionStateExceptionAndLeavesZeroRows() {
        AuditActor actor = new AuditActor(null, "SYSTEM");
        AuditTarget target = new AuditTarget("CATEGORY", "cat-1");

        assertThatThrownBy(() -> recorder.record("CATEGORY_CREATED", actor, target, Map.of()))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(rows()).isZero();
    }

    @Test
    void undeclaredActionOrKeyThrowsIllegalArgumentExceptionAndLeavesZeroRows() {
        AuditActor actor = new AuditActor(UUID.randomUUID(), "ADMIN");
        AuditTarget target = new AuditTarget("SELLER_PROFILE", "acc-1");

        assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                recorder.record("UNKNOWN_ACTION", actor, target, Map.of())
        )).isInstanceOf(IllegalArgumentException.class);

        assertThat(rows()).isZero();

        assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                recorder.record("SELLER_PROFILE_REJECTED", actor, target, Map.of("undeclaredKey", "val"))
        )).isInstanceOf(IllegalArgumentException.class);

        assertThat(rows()).isZero();
    }

    @Test
    void mdcTraceIdIsPropagatedToOutboxRowAndAuditRecord() {
        MDC.put("traceId", "trace-abc");
        AuditActor actor = new AuditActor(null, "SYSTEM");
        AuditTarget target = new AuditTarget("CATEGORY", "cat-2");

        tx.executeWithoutResult(status ->
                recorder.record("CATEGORY_CREATED", actor, target, Map.of())
        );

        assertThat(rows()).isEqualTo(1);
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM outbox_events");
        assertThat(row.get("trace_id")).isEqualTo("trace-abc");

        EventEnvelope envelope = new EventEnvelopeReader(objectMapper).read((String) row.get("payload"));
        AuditRecord auditRecord = AuditRecordReader.read(envelope);
        assertThat(auditRecord.traceId()).isEqualTo("trace-abc");
    }
}
