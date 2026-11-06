package vn.t3nexus.lib.eventconsumer;

import io.micrometer.core.instrument.MeterRegistry;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import vn.t3nexus.consumertest.TestApplication;
import vn.t3nexus.consumertest.TestApplication.SimulatedHandler;
import vn.t3nexus.lib.events.EventEnvelopeFactory;
import vn.t3nexus.lib.events.SensitiveFieldPolicy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * KT-05 (giao lại), KT-06 (thử lại, hàng chờ lỗi, cảnh báo), KT-07 (thông điệp hỏng không chặn dòng) và KT-09 phía nhận
 * (mã truy vết), trên một Kafka thật chạy trong tiến trình (EmbeddedKafka, KRaft). Thử lại và giãn cách thu ngắn bằng cấu hình.
 */
@SpringBootTest(classes = TestApplication.class, properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.application.name=consumer-test",
        "test.topic=" + EventConsumerKafkaIntegrationTest.TOPIC,
        "app.event-consumer.retry.max-retries=3",
        "app.event-consumer.retry.initial-interval=20ms",
        "app.event-consumer.retry.multiplier=2.0",
        "app.event-consumer.retry.max-interval=100ms"
})
@EmbeddedKafka(partitions = 1, topics = {EventConsumerKafkaIntegrationTest.TOPIC, EventConsumerKafkaIntegrationTest.DLQ_TOPIC})
@ExtendWith(OutputCaptureExtension.class)
class EventConsumerKafkaIntegrationTest {

    static final String TOPIC = "consumer.test.events";
    /** Một hàng chờ lỗi mỗi dịch vụ nhận: {@code <spring.application.name>.dlq} (ADR-0010), không theo chủ đề gốc. */
    static final String DLQ_TOPIC = "consumer-test.dlq";

    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired EmbeddedKafkaBroker broker;
    @Autowired SimulatedHandler handler;
    @Autowired MeterRegistry meters;
    @Autowired ObjectMapper mapper;

    private final EventEnvelopeFactory factory = new EventEnvelopeFactory(JsonMapper.builder().build(), SensitiveFieldPolicy.strict());

    private String envelope(String eventId, Object payload) {
        return factory.toJson(factory.create(eventId, "Probe", "agg-1", "ProbeEvent", Instant.parse("2026-10-24T00:00:00Z"),
                "trace-" + eventId, "span-" + eventId, 1, payload));
    }

    private String newId() {
        return "ev-" + UUID.randomUUID();
    }

    /** Gửi như bộ chuyển hộp thư: tiêu đề {@code id} là mã event (nếu nội dung có mã event). */
    private void send(String key, String body) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, key, body);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"eventId\":\"([^\"]+)\"").matcher(body);
        if (m.find()) {
            record.headers().add("id", m.group(1).getBytes(StandardCharsets.UTF_8));
        }
        kafka.send(record).get(10, TimeUnit.SECONDS);
    }

    private List<ConsumerRecord<String, String>> dlq() {
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

    private List<ConsumerRecord<String, String>> dlqOf(String eventId) {
        return dlq().stream().filter(r -> r.value() != null && r.value().contains(eventId)).toList();
    }

    private static String header(ConsumerRecord<?, ?> r, String name) {
        Header h = r.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }

    // ---- KT-09 phía nhận ----

    @Test
    void validEventIsHandledOnceWithTraceContextAndContextIsClearedAfterwards() throws Exception {
        String id = newId();
        send("agg-1", envelope(id, Map.of("mode", "ok")));

        await().atMost(Duration.ofSeconds(20)).until(() -> handler.effects.contains(id));

        assertThat(handler.attemptsOf(id)).isEqualTo(1);
        assertThat(handler.mdcInside.get(id)).containsEntry("traceId", "trace-" + id).containsEntry("spanId", "span-" + id)
                .containsEntry("eventId", id).containsEntry("eventType", "ProbeEvent");
        await().atMost(Duration.ofSeconds(5)).until(() -> handler.mdcAfterConsume.contains("null|null"));
    }

    // ---- KT-06 ----

    @Test
    void transientFailureIsRetriedThenSucceedsWithoutDeadLettering() throws Exception {
        String id = newId();
        send("agg-1", envelope(id, Map.of("mode", "transient", "failTimes", 2)));

        await().atMost(Duration.ofSeconds(20)).until(() -> handler.effects.contains(id));

        assertThat(handler.attemptsOf(id)).isEqualTo(3);
        assertThat(handler.effectApplications.get()).isGreaterThanOrEqualTo(1);
        assertThat(dlqOf(id)).isEmpty();
    }

    @Test
    void exhaustedRetriesGoToDeadLetterTopicAndTheNextEventStillRuns(CapturedOutput output) throws Exception {
        String bad = newId();
        String good = newId();
        double before = deadLettered();
        String badBody = envelope(bad, Map.of("mode", "always"));
        send("agg-1", badBody);
        send("agg-1", envelope(good, Map.of("mode", "ok")));

        await().atMost(Duration.ofSeconds(30)).until(() -> handler.effects.contains(good));

        assertThat(handler.attemptsOf(bad)).isEqualTo(4);               // lần đầu + 3 lần thử lại
        List<ConsumerRecord<String, String>> dead = dlqOf(bad);
        assertThat(dead).hasSize(1);
        ConsumerRecord<String, String> r = dead.get(0);
        assertThat(r.key()).isEqualTo("agg-1");
        assertThat(r.value()).isEqualTo(badBody);                          // giữ nguyên giá trị và khóa
        assertThat(header(r, "x-dlq-error-class")).isEqualTo("java.lang.IllegalStateException");
        assertThat(header(r, "kafka_dlt-original-topic")).isEqualTo(TOPIC);
        for (Header h : r.headers()) {                                      // không có thông báo lỗi, không có dấu vết ngăn xếp
            assertThat(new String(h.value(), StandardCharsets.UTF_8)).doesNotContain("hunter2");
            assertThat(h.key()).doesNotContain("exception-message").doesNotContain("stacktrace");
        }
        assertThat(output.getAll()).contains("EVENT_DEAD_LETTERED").contains("topic=" + TOPIC).contains("eventId=" + bad)
                .contains("error=java.lang.IllegalStateException");
        assertThat(output.getAll()).doesNotContain("hunter2");
        assertThat(deadLettered() - before).isEqualTo(1.0);
    }

    // ---- KT-07 ----

    @Test
    void invalidEnvelopeGoesToDeadLetterTopicWithoutRetryAndDoesNotBlockTheNextEvent(CapturedOutput output) throws Exception {
        String good = newId();
        String poison = "{\"password\":\"hunter2-do-not-leak\"," + "\"marker\":\"" + good + "\"";   // JSON hỏng có bí mật
        send("agg-1", poison);
        send("agg-1", envelope(good, Map.of("mode", "ok")));

        await().atMost(Duration.ofSeconds(20)).until(() -> handler.effects.contains(good));

        assertThat(dlq().stream().anyMatch(r -> poison.equals(r.value()))).isTrue();
        assertThat(output.getAll()).doesNotContain("hunter2");
        assertThat(output.getAll()).doesNotContain("attempt=2");              // lỗi không thể thử lại: không có lần thử lại nào
    }

    @Test
    void handlerDeclaredNonRetryableGoesToDeadLetterAfterASingleAttempt() throws Exception {
        String id = newId();
        send("agg-1", envelope(id, Map.of("mode", "poison")));

        await().atMost(Duration.ofSeconds(20)).until(() -> !dlqOf(id).isEmpty());

        assertThat(handler.attemptsOf(id)).isEqualTo(1);
        assertThat(header(dlqOf(id).get(0), "x-dlq-error-class")).isEqualTo(NonRetryableEventException.class.getName());
    }

    // ---- KT-05 ----

    @Test
    void redeliveryAfterTheEffectWasAppliedLeavesTheEffectApplyOnce() throws Exception {
        String id = newId();
        int before = handler.effectApplications.get();
        send("agg-1", envelope(id, Map.of("mode", "failAfterEffectOnce")));   // hiệu lực đã ghi, chết trước ack, giao lại

        await().atMost(Duration.ofSeconds(20)).until(() -> handler.attemptsOf(id) >= 2);
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(5)).until(() -> handler.attemptsOf(id) == 2);

        assertThat(handler.effectApplications.get() - before).isEqualTo(1);
        assertThat(dlqOf(id)).isEmpty();
    }

    @Test
    void theSameEventPublishedTwiceHasTheEffectOnce() throws Exception {
        String id = newId();
        int before = handler.effectApplications.get();
        String body = envelope(id, Map.of("mode", "ok"));
        send("agg-1", body);
        send("agg-1", body);

        await().atMost(Duration.ofSeconds(20)).until(() -> handler.attemptsOf(id) == 2);

        assertThat(handler.effectApplications.get() - before).isEqualTo(1);
    }

    private double deadLettered() {
        var c = meters.find("events.dead_lettered").tag("topic", TOPIC).counter();
        return c == null ? 0 : c.count();
    }
}
