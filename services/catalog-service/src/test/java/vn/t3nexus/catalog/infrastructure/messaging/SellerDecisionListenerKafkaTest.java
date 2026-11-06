package vn.t3nexus.catalog.infrastructure.messaging;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.catalog.CatalogPostgresTestBase;
import vn.t3nexus.catalog.CatalogServiceApplication;

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

/**
 * KT-07 (feature 09): nhận kết quả duyệt người bán từ Kafka (embedded) và Postgres thật. Ghi đè theo phiên bản hồ sơ,
 * nhận lặp, sai thứ tự, khung hỏng vào hàng chờ lỗi (ADR-0010).
 */
@SpringBootTest(classes = CatalogServiceApplication.class, properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "app.event-consumer.retry.max-retries=2",
        "app.event-consumer.retry.initial-interval=20ms",
        "app.event-consumer.retry.multiplier=2.0",
        "app.event-consumer.retry.max-interval=100ms"
})
@EmbeddedKafka(partitions = 1, topics = {"seller.profile.approved", "seller.profile.rejected", "catalog-service.dlq"})
class SellerDecisionListenerKafkaTest extends CatalogPostgresTestBase {

    private static final String APPROVED = "seller.profile.approved";
    private static final String REJECTED = "seller.profile.rejected";
    private static final String DLQ = "catalog-service.dlq";

    @Autowired
    private KafkaTemplate<String, String> kafka;
    @Autowired
    private EmbeddedKafkaBroker broker;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE TABLE seller_state");
    }

    private void send(String topic, UUID account, Object accountIdValue, Object version) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accountId", accountIdValue);
        payload.put("profileId", UUID.randomUUID().toString());
        payload.put("version", version);
        payload.put("occurredAt", "2026-11-06T00:00:00Z");
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("aggregateId", account.toString());
        envelope.put("aggregateType", "SellerProfile");
        envelope.put("eventType", topic.equals(APPROVED) ? "SellerProfileApproved" : "SellerProfileRejected");
        envelope.put("occurredAt", "2026-11-06T00:00:00Z");
        envelope.put("traceId", "trace-" + UUID.randomUUID());
        envelope.put("schemaVersion", 1);
        envelope.put("payload", payload);
        kafka.send(new ProducerRecord<>(topic, account.toString(), objectMapper.writeValueAsString(envelope)))
                .get(10, TimeUnit.SECONDS);
    }

    private String stateOf(UUID account) {
        List<String> rows = jdbc.queryForList("SELECT status || ':' || version FROM seller_state WHERE account_id = ?",
                String.class, account);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Test
    @DisplayName("Được duyệt rồi bị từ chối với phiên bản cao hơn: ghi đè theo phiên bản")
    void newerVersionOverwrites() throws Exception {
        UUID account = UUID.randomUUID();
        send(APPROVED, account, account.toString(), 3);
        await().atMost(15, TimeUnit.SECONDS).until(() -> "APPROVED:3".equals(stateOf(account)));

        send(REJECTED, account, account.toString(), 5);
        await().atMost(15, TimeUnit.SECONDS).until(() -> "REJECTED:5".equals(stateOf(account)));
    }

    @Test
    @DisplayName("Sự kiện cũ hơn đến sau không kéo trạng thái lùi; nhận lặp vô hại")
    void olderVersionAndDuplicatesIgnored() throws Exception {
        UUID account = UUID.randomUUID();
        send(APPROVED, account, account.toString(), 5);
        await().atMost(15, TimeUnit.SECONDS).until(() -> "APPROVED:5".equals(stateOf(account)));

        send(APPROVED, account, account.toString(), 5);
        send(REJECTED, account, account.toString(), 3);
        // Một sự kiện đánh dấu đến sau cùng, cùng khóa nên cùng phân vùng: khi nó đã được xử lý, hai sự kiện trước chắc chắn xong
        UUID marker = UUID.randomUUID();
        send(APPROVED, marker, marker.toString(), 1);
        await().atMost(15, TimeUnit.SECONDS).until(() -> "APPROVED:1".equals(stateOf(marker)));

        assertThat(stateOf(account)).isEqualTo("APPROVED:5");
    }

    @Test
    @DisplayName("Bị từ chối lần đầu rồi nộp lại và được duyệt (phiên bản cao hơn): thành APPROVED")
    void resubmissionApproved() throws Exception {
        UUID account = UUID.randomUUID();
        send(REJECTED, account, account.toString(), 2);
        await().atMost(15, TimeUnit.SECONDS).until(() -> "REJECTED:2".equals(stateOf(account)));

        send(APPROVED, account, account.toString(), 4);
        await().atMost(15, TimeUnit.SECONDS).until(() -> "APPROVED:4".equals(stateOf(account)));
    }

    @Test
    @DisplayName("Khung hỏng (mã tài khoản không phải UUID, phiên bản âm) vào hàng chờ lỗi, không ghi gì")
    void malformedGoesToDeadLetter() throws Exception {
        UUID key = UUID.randomUUID();
        send(APPROVED, key, "khong-phai-uuid", 1);
        send(REJECTED, key, UUID.randomUUID().toString(), -1);

        await().atMost(20, TimeUnit.SECONDS).until(() -> pollDlq().size() >= 2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM seller_state", Long.class)).isZero();
    }

    private List<ConsumerRecord<String, String>> pollDlq() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlq-reader-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        List<ConsumerRecord<String, String>> all = new ArrayList<>();
        try (KafkaConsumer<String, String> c = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            c.subscribe(List.of(DLQ));
            long end = System.currentTimeMillis() + 1500;
            while (System.currentTimeMillis() < end) {
                c.poll(Duration.ofMillis(300)).forEach(all::add);
            }
        }
        return all;
    }
}
