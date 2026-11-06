package vn.t3nexus.audit.presentation.auditentry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import vn.t3nexus.audit.AuditPostgresTestBase;
import vn.t3nexus.audit.AuditServiceApplication;
import vn.t3nexus.audit.infrastructure.messaging.AuditActionRecordedListener;
import vn.t3nexus.lib.audit.AuditActionRecorded;
import vn.t3nexus.lib.audit.AuditActor;
import vn.t3nexus.lib.audit.AuditTarget;
import vn.t3nexus.lib.events.EventEnvelopeFactory;
import vn.t3nexus.lib.events.SensitiveFieldPolicy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chuỗi trong Maven (bước 7): lớp thật của bên phát dựng bản ghi, khung đóng như hộp thư làm, listener thật của Nhật ký nhận,
 * dữ liệu vào Postgres, rồi tra cứu qua API. Chứng minh hợp đồng khép hai chiều trước khi chạy với Kafka Connect thật.
 * <br>Hàng hộp thư thật (OutboxEventStore, Postgres) đã được chứng minh ở AuditRecorderIntegrationTest của mô-đun audit-events;
 * audit-service cố ý không kéo outbox-starter, nên test này đóng khung bằng đúng lời gọi EventEnvelopeFactory mà OutboxEventStore
 * dùng (cùng tham số, cùng phiên bản khung), không bằng JSON viết tay: tên trường lệch giữa bên dựng và bên đọc sẽ làm test đỏ.
 * <br>Bảng không xóa được nên mỗi ca dùng đối tượng riêng và chỉ xét dòng của mình.
 */
@SpringBootTest(classes = AuditServiceApplication.class, properties = {
        "spring.kafka.listener.auto-startup=false"
})
class AuditChainIntegrationTest extends AuditPostgresTestBase {

    /** Phiên bản khung mà OutboxEventStore ghi (OutboxEventStore.SCHEMA_VERSION). */
    private static final int ENVELOPE_SCHEMA_VERSION = 1;

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private AuditActionRecordedListener listener;
    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private final EventEnvelopeFactory envelopeFactory =
            new EventEnvelopeFactory(JsonMapper.builder().build(), SensitiveFieldPolicy.strict());

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    /** Đóng khung đúng như OutboxEventStore.store: eventType là tên lớp, aggregate lấy từ sự kiện, payload của sự kiện. */
    private String asOutboxPayload(AuditActionRecorded event, String traceId) {
        return envelopeFactory.toJson(envelopeFactory.create(
                event.getEventId(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getClass().getSimpleName(),
                event.getOccurredOn(),
                traceId,
                null,
                ENVELOPE_SCHEMA_VERSION,
                event.getPayload()));
    }

    private JsonNode listByTarget(String targetType, String targetId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/audit/entries")
                        .param("targetType", targetType)
                        .param("targetId", targetId))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("items");
    }

    private JsonNode detail(String id) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/audit/entries/" + id)).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    @Test
    void realSenderEventReachesTheApiWithEveryField() throws Exception {
        UUID adminId = UUID.randomUUID();
        String targetType = "SELLER_PROFILE";
        String targetId = "p-" + UUID.randomUUID();
        Instant occurredOn = Instant.parse("2026-10-25T03:04:05.123456789Z");
        AuditActionRecorded event = new AuditActionRecorded(
                "ev-" + UUID.randomUUID(), occurredOn, "seller-service",
                new AuditActor(adminId, "ADMIN"), "SELLER_PROFILE_REJECTED",
                new AuditTarget(targetType, targetId),
                Map.of("profileVersion", 3, "reason", "Giấy phép kinh doanh bị mờ"));

        listener.on(asOutboxPayload(event, "trace-chain-1"));

        JsonNode items = listByTarget(targetType, targetId);
        assertThat(items).hasSize(1);
        JsonNode item = items.get(0);
        assertThat(item.path("action").asText()).isEqualTo("SELLER_PROFILE_REJECTED");
        assertThat(item.path("actor").path("accountId").asText()).isEqualTo(adminId.toString());
        assertThat(item.path("actor").path("role").asText()).isEqualTo("ADMIN");
        assertThat(item.path("target").path("type").asText()).isEqualTo(targetType);
        assertThat(item.path("target").path("id").asText()).isEqualTo(targetId);
        assertThat(item.path("sourceService").asText()).isEqualTo("seller-service");
        // Danh sách không có chi tiết
        assertThat(item.has("details")).isFalse();

        JsonNode full = detail(item.path("id").asText());
        assertThat(full.path("sourceEventId").asText()).isEqualTo(event.getEventId());
        assertThat(full.path("traceId").asText()).isEqualTo("trace-chain-1");
        // Thời điểm xảy ra đi hết chuỗi, chỉ mất phần dưới micro giây
        assertThat(Instant.parse(full.path("occurredAt").asText())).isEqualTo(occurredOn.truncatedTo(ChronoUnit.MICROS));
        assertThat(full.path("details").path("profileVersion").asInt()).isEqualTo(3);
        assertThat(full.path("details").path("reason").asText()).isEqualTo("Giấy phép kinh doanh bị mờ");
        assertThat(full.path("details").size()).isEqualTo(2);
    }

    @Test
    void systemActorEventWithoutAccountReachesTheApi() throws Exception {
        String targetId = "c-" + UUID.randomUUID();
        AuditActionRecorded event = new AuditActionRecorded(
                "ev-" + UUID.randomUUID(), Instant.parse("2026-10-25T05:00:00Z"), "scheduler-service",
                new AuditActor(null, "SYSTEM"), "ORDER_AUTO_CANCELLED",
                new AuditTarget("ORDER", targetId), Map.of());

        listener.on(asOutboxPayload(event, null));

        JsonNode items = listByTarget("ORDER", targetId);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).path("actor").path("role").asText()).isEqualTo("SYSTEM");
        assertThat(items.get(0).path("actor").path("accountId").isNull()).isTrue();
        JsonNode full = detail(items.get(0).path("id").asText());
        assertThat(full.path("details").size()).isZero();
        assertThat(full.path("traceId").isNull()).isTrue();
    }

    @Test
    void sameRealEventDeliveredTwiceGivesOneEntry() throws Exception {
        String targetId = "d-" + UUID.randomUUID();
        AuditActionRecorded event = new AuditActionRecorded(
                "ev-" + UUID.randomUUID(), Instant.parse("2026-10-25T06:00:00Z"), "catalog-service",
                new AuditActor(UUID.randomUUID(), "ADMIN"), "CATEGORY_CREATED",
                new AuditTarget("CATEGORY", targetId), Map.of("version", 1));
        String body = asOutboxPayload(event, "trace-chain-3");

        listener.on(body);
        listener.on(body);

        assertThat(listByTarget("CATEGORY", targetId)).hasSize(1);
    }
}
