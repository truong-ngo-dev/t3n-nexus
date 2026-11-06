package vn.t3nexus.audit.infrastructure.messaging;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.audit.AuditPostgresTestBase;
import vn.t3nexus.audit.AuditServiceApplication;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = AuditServiceApplication.class, properties = {
        "spring.main.web-application-type=none",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "app.audit.topic=audit.test.recorded",
        "app.event-consumer.retry.max-retries=3",
        "app.event-consumer.retry.initial-interval=20ms",
        "app.event-consumer.retry.multiplier=2.0",
        "app.event-consumer.retry.max-interval=100ms"
})
@EmbeddedKafka(partitions = 1, topics = {"audit.test.recorded", "audit-service.dlq"})
@ExtendWith(OutputCaptureExtension.class)
class AuditActionRecordedListenerKafkaIntegrationTest extends AuditPostgresTestBase {

    private static final String TOPIC = "audit.test.recorded";
    private static final String DLQ_TOPIC = "audit-service.dlq";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private void send(String key, String body) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, key, body);
        kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
    }

    private List<ConsumerRecord<String, String>> pollDlq() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlq-reader-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        List<ConsumerRecord<String, String>> all = new ArrayList<>();
        try (KafkaConsumer<String, String> c = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            c.subscribe(List.of(DLQ_TOPIC));
            long end = System.currentTimeMillis() + 2500;
            while (System.currentTimeMillis() < end) {
                c.poll(Duration.ofMillis(300)).forEach(all::add);
            }
        }
        return all;
    }

    private String header(ConsumerRecord<?, ?> r, String name) {
        Header h = r.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }

    private String buildEnvelopeJson(
            String eventId,
            String traceId,
            Integer schemaVersion,
            String action,
            Map<String, Object> actorMap,
            Map<String, Object> targetMap,
            Map<String, Object> detailsMap
    ) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceService", "seller-service");
        if (actorMap != null) {
            payload.put("actor", actorMap);
        }
        if (action != null) {
            payload.put("action", action);
        }
        if (targetMap != null) {
            payload.put("target", targetMap);
        }
        if (detailsMap != null) {
            payload.put("details", detailsMap);
        }

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("aggregateId", targetMap != null ? targetMap.get("id") : "target-default");
        envelope.put("aggregateType", "TARGET");
        envelope.put("eventType", "AuditActionRecorded");
        envelope.put("occurredAt", "2026-10-24T10:00:00Z");
        envelope.put("traceId", traceId);
        envelope.put("schemaVersion", schemaVersion);
        envelope.put("payload", payload);

        return objectMapper.writeValueAsString(envelope);
    }

    @Test
    void validMessageCreatesSingleRowWithAllFields() throws Exception {
        String eventId = "ev-" + UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String json = buildEnvelopeJson(
                eventId,
                "trace-valid-1",
                1,
                "SELLER_PROFILE_APPROVED",
                Map.of("accountId", actorId.toString(), "role", "ADMIN"),
                Map.of("type", "SELLER_PROFILE", "id", "p-1"),
                Map.of("reason", "valid_profile")
        );

        send("p-1", json);

        await().atMost(Duration.ofSeconds(20)).until(() -> {
            Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM audit_entries WHERE source_event_id = ?", Integer.class, eventId);
            return count != null && count == 1;
        });

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM audit_entries WHERE source_event_id = ?", eventId);
        assertThat(row.get("source_service")).isEqualTo("seller-service");
        assertThat(row.get("actor_account_id")).isEqualTo(actorId);
        assertThat(row.get("actor_role")).isEqualTo("ADMIN");
        assertThat(row.get("action")).isEqualTo("SELLER_PROFILE_APPROVED");
        assertThat(row.get("target_type")).isEqualTo("SELLER_PROFILE");
        assertThat(row.get("target_id")).isEqualTo("p-1");
        assertThat(row.get("trace_id")).isEqualTo("trace-valid-1");
    }

    @Test
    void sameMessagePublishedTwiceResultsInSingleRow() throws Exception {
        String eventId = "ev-" + UUID.randomUUID();
        String json = buildEnvelopeJson(
                eventId,
                "trace-dup",
                1,
                "SELLER_PROFILE_APPROVED",
                Map.of("accountId", UUID.randomUUID().toString(), "role", "ADMIN"),
                Map.of("type", "SELLER_PROFILE", "id", "p-dup"),
                Map.of()
        );

        send("p-dup", json);
        send("p-dup", json);

        await().atMost(Duration.ofSeconds(20)).until(() -> {
            Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM audit_entries WHERE source_event_id = ?", Integer.class, eventId);
            return count != null && count == 1;
        });

        Thread.sleep(1000);
        Integer countAfter = jdbcTemplate.queryForObject("SELECT count(*) FROM audit_entries WHERE source_event_id = ?", Integer.class, eventId);
        assertThat(countAfter).isEqualTo(1);
    }

    @Test
    void unfamiliarValidActionIsSaved() throws Exception {
        String eventId = "ev-" + UUID.randomUUID();
        String json = buildEnvelopeJson(
                eventId,
                "trace-unfamiliar",
                1,
                "WIDGET_FROBNICATED",
                Map.of("accountId", UUID.randomUUID().toString(), "role", "ADMIN"),
                Map.of("type", "WIDGET", "id", "w-1"),
                Map.of()
        );

        send("w-1", json);

        await().atMost(Duration.ofSeconds(20)).until(() -> {
            Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM audit_entries WHERE source_event_id = ?", Integer.class, eventId);
            return count != null && count == 1;
        });

        String action = jdbcTemplate.queryForObject("SELECT action FROM audit_entries WHERE source_event_id = ?", String.class, eventId);
        assertThat(action).isEqualTo("WIDGET_FROBNICATED");
    }

    @Test
    void corruptedMessagesGoToDeadLetterTopicWithoutBlockingSubsequentValidMessage(CapturedOutput output) throws Exception {
        UUID validActorId = UUID.randomUUID();

        // 1. Thiếu target
        String idMissingTarget = "ev-" + UUID.randomUUID();
        Map<String, Object> payload1 = new LinkedHashMap<>();
        payload1.put("sourceService", "seller-service");
        payload1.put("actor", Map.of("accountId", validActorId.toString(), "role", "ADMIN"));
        payload1.put("action", "SELLER_PROFILE_APPROVED");
        Map<String, Object> env1 = new LinkedHashMap<>();
        env1.put("eventId", idMissingTarget);
        env1.put("aggregateId", "agg-1");
        env1.put("aggregateType", "TARGET");
        env1.put("eventType", "AuditActionRecorded");
        env1.put("occurredAt", "2026-10-24T10:00:00Z");
        env1.put("schemaVersion", 1);
        env1.put("payload", payload1);
        send("k1", objectMapper.writeValueAsString(env1));

        // 2. Action chữ thường
        String idLowerAction = "ev-" + UUID.randomUUID();
        send("k2", buildEnvelopeJson(
                idLowerAction, "t2", 1, "seller_profile_approved",
                Map.of("accountId", validActorId.toString(), "role", "ADMIN"),
                Map.of("type", "SELLER_PROFILE", "id", "p-1"),
                Map.of()
        ));

        // 3. SchemaVersion 0
        String idBadSchema = "ev-" + UUID.randomUUID();
        send("k3", buildEnvelopeJson(
                idBadSchema, "t3", 0, "SELLER_PROFILE_APPROVED",
                Map.of("accountId", validActorId.toString(), "role", "ADMIN"),
                Map.of("type", "SELLER_PROFILE", "id", "p-1"),
                Map.of()
        ));

        // 4. Chuỗi không phải JSON
        String idNotJson = "not-a-json-string-content";
        send("k4", idNotJson);

        // 5. Actor role USER không có accountId
        String idUserNoAccount = "ev-" + UUID.randomUUID();
        Map<String, Object> actorWithoutAccount = new LinkedHashMap<>();
        actorWithoutAccount.put("role", "USER");
        send("k5", buildEnvelopeJson(
                idUserNoAccount, "t5", 1, "SELLER_PROFILE_APPROVED",
                actorWithoutAccount,
                Map.of("type", "SELLER_PROFILE", "id", "p-1"),
                Map.of()
        ));

        // 6. Gửi tiếp thông điệp hợp lệ ngay sau
        String validId = "ev-" + UUID.randomUUID();
        send("k-valid", buildEnvelopeJson(
                validId, "t-valid", 1, "SELLER_PROFILE_APPROVED",
                Map.of("accountId", validActorId.toString(), "role", "ADMIN"),
                Map.of("type", "SELLER_PROFILE", "id", "p-valid"),
                Map.of()
        ));

        await().atMost(Duration.ofSeconds(25)).until(() -> {
            Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM audit_entries WHERE source_event_id = ?", Integer.class, validId);
            return count != null && count == 1;
        });

        // Kiểm tra không có dòng nào cho các thông điệp hỏng
        List<String> corruptedIds = List.of(idMissingTarget, idLowerAction, idBadSchema, idUserNoAccount);
        for (String cId : corruptedIds) {
            Integer cCount = jdbcTemplate.queryForObject("SELECT count(*) FROM audit_entries WHERE source_event_id = ?", Integer.class, cId);
            assertThat(cCount).isZero();
        }

        // Mỗi thông điệp hỏng nằm trong hàng chờ lỗi (cả thông điệp không phải JSON), kèm tiêu đề tên loại lỗi
        List<ConsumerRecord<String, String>> dlqRecords = pollDlq();
        for (String cId : corruptedIds) {
            assertThat(dlqRecords).as("hàng chờ lỗi có %s", cId).anyMatch(r -> r.value() != null && r.value().contains(cId));
        }
        assertThat(dlqRecords).as("hàng chờ lỗi có thông điệp không phải JSON").anyMatch(r -> idNotJson.equals(r.value()));
        for (ConsumerRecord<String, String> r : dlqRecords) {
            assertThat(header(r, "x-dlq-error-class")).isNotNull();
        }

        // Lỗi hợp đồng là lỗi không thể thử lại: mỗi thông điệp hỏng chỉ bị giao đúng một lần, không có lần thử thứ hai
        // (bộ nhận thử lại tối đa 3 lần cho lỗi tạm, nên nếu bị phân loại nhầm thành lỗi tạm sẽ thấy attempt=2)
        String log = output.getAll();
        for (String cId : corruptedIds) {
            assertThat(log).as("lần giao đầu của %s", cId).containsPattern("attempt=1 [^\n]*eventId=" + cId);
            assertThat(log).as("không thử lại %s", cId).doesNotContainPattern("attempt=[2-9] [^\n]*eventId=" + cId);
        }
    }

    @Test
    void droppedDetailsAreFilteredAndLoggedWithoutValue(CapturedOutput output) throws Exception {
        String eventId = "ev-" + UUID.randomUUID();
        String secretEmail = "secret-victim@example.com";
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("email", secretEmail);
        details.put("reason", "valid_reason");

        String json = buildEnvelopeJson(
                eventId,
                "trace-dropped",
                1,
                "SELLER_PROFILE_APPROVED",
                Map.of("accountId", UUID.randomUUID().toString(), "role", "ADMIN"),
                Map.of("type", "SELLER_PROFILE", "id", "p-dropped"),
                details
        );

        send("p-dropped", json);

        await().atMost(Duration.ofSeconds(20)).until(() -> {
            Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM audit_entries WHERE source_event_id = ?", Integer.class, eventId);
            return count != null && count == 1;
        });

        String detailsJson = jdbcTemplate.queryForObject(
                "SELECT details::text FROM audit_entries WHERE source_event_id = ?",
                String.class,
                eventId
        );
        var detailsTree = tools.jackson.databind.json.JsonMapper.builder().build().readTree(detailsJson);
        assertThat(detailsTree.get("reason").asText()).isEqualTo("valid_reason");
        assertThat(detailsTree.has("email")).isFalse();
        assertThat(detailsTree.size()).isEqualTo(1);
        assertThat(detailsJson).doesNotContain(secretEmail);

        assertThat(output.getAll()).contains("AUDIT_DETAILS_KEY_DROPPED");
        assertThat(output.getAll()).contains("key=email");
        assertThat(output.getAll()).doesNotContain(secretEmail);
    }

    @Test
    void traceIdFromMessageIsStoredInColumn() throws Exception {
        String eventId = "ev-" + UUID.randomUUID();
        String traceId = "test-distributed-trace-xyz-987";
        String json = buildEnvelopeJson(
                eventId,
                traceId,
                1,
                "SELLER_PROFILE_APPROVED",
                Map.of("accountId", UUID.randomUUID().toString(), "role", "ADMIN"),
                Map.of("type", "SELLER_PROFILE", "id", "p-trace"),
                Map.of()
        );

        send("p-trace", json);

        await().atMost(Duration.ofSeconds(20)).until(() -> {
            Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM audit_entries WHERE source_event_id = ?", Integer.class, eventId);
            return count != null && count == 1;
        });

        String savedTraceId = jdbcTemplate.queryForObject(
                "SELECT trace_id FROM audit_entries WHERE source_event_id = ?",
                String.class,
                eventId
        );
        assertThat(savedTraceId).isEqualTo(traceId);
    }
}
