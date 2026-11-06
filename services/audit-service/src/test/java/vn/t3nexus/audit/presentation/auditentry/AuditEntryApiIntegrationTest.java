package vn.t3nexus.audit.presentation.auditentry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.audit.AuditPostgresTestBase;
import vn.t3nexus.audit.AuditServiceApplication;
import vn.t3nexus.audit.domain.auditentry.AuditEntry;
import vn.t3nexus.audit.domain.auditentry.AuditEntryRepository;
import vn.t3nexus.lib.audit.AuditActor;
import vn.t3nexus.lib.audit.AuditTarget;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AuditServiceApplication.class, properties = {
        "spring.kafka.listener.auto-startup=false"
})
@ExtendWith(OutputCaptureExtension.class)
class AuditEntryApiIntegrationTest extends AuditPostgresTestBase {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private AuditEntryRepository repository;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Dựng MockMvc bằng webAppContextSetup mà không dùng @AutoConfigureMockMvc
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    private AuditEntry createAndInsert(UUID actorId, String action, String targetType, String targetId, Instant occurredAt, UUID id, Map<String, Object> details, String traceId) {
        AuditEntry entry = AuditEntry.restore(
                id,
                "ev-" + UUID.randomUUID(),
                occurredAt.truncatedTo(ChronoUnit.MICROS),
                Instant.now().truncatedTo(ChronoUnit.MICROS),
                new AuditActor(actorId, "ADMIN"),
                action,
                new AuditTarget(targetType, targetId),
                details != null ? details : Map.of(),
                "audit-test-service",
                traceId
        );
        repository.insertIfAbsent(entry);
        return entry;
    }

    @Test
    void kt09_kt12_listAuditEntriesFieldsFilteringAndPagination() throws Exception {
        // KT-09/12: Kiểm tra danh sách trả về đúng cấu trúc summary, không chứa details/traceId/sourceEventId
        UUID actorId = UUID.randomUUID();
        String targetType = "T" + System.nanoTime();
        Instant t1 = Instant.parse("2026-10-25T10:00:00Z");
        Instant t2 = Instant.parse("2026-10-25T11:00:00Z");

        AuditEntry e1 = createAndInsert(actorId, "ACT_LIST_1", targetType, "tgt-1", t1, UUID.randomUUID(), Map.of("secretKey", "secretVal1"), "trace-list-1");
        AuditEntry e2 = createAndInsert(actorId, "ACT_LIST_2", targetType, "tgt-2", t2, UUID.randomUUID(), Map.of("secretKey", "secretVal2"), "trace-list-2");

        // Gọi GET với limit=1 để kiểm tra nextCursor
        MvcResult resPage1 = mockMvc.perform(get("/api/audit/entries")
                        .param("actorId", actorId.toString())
                        .param("targetType", targetType)
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].id").value(e2.id().toString()))
                .andExpect(jsonPath("$.data.items[0].occurredAt").isString())
                .andExpect(jsonPath("$.data.items[0].recordedAt").isString())
                .andExpect(jsonPath("$.data.items[0].actor.accountId").value(actorId.toString()))
                .andExpect(jsonPath("$.data.items[0].actor.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.items[0].action").value("ACT_LIST_2"))
                .andExpect(jsonPath("$.data.items[0].target.type").value(targetType))
                .andExpect(jsonPath("$.data.items[0].target.id").value("tgt-2"))
                .andExpect(jsonPath("$.data.items[0].sourceService").value("audit-test-service"))
                .andExpect(jsonPath("$.data.items[0].details").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].traceId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].sourceEventId").doesNotExist())
                .andExpect(jsonPath("$.data.nextCursor").isNotEmpty())
                .andReturn();

        JsonNode jsonPage1 = objectMapper.readTree(resPage1.getResponse().getContentAsString());
        String nextCursor = jsonPage1.path("data").path("nextCursor").asText();

        // Lấy trang tiếp theo bằng nextCursor
        mockMvc.perform(get("/api/audit/entries")
                        .param("actorId", actorId.toString())
                        .param("targetType", targetType)
                        .param("cursor", nextCursor)
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].id").value(e1.id().toString()))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist()); // Trang cuối nextCursor null/absent

        // Kiểm tra lọc bằng từng tham số riêng
        mockMvc.perform(get("/api/audit/entries")
                        .param("targetType", targetType)
                        .param("action", "ACT_LIST_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(e1.id().toString()));
    }

    @Test
    void kt10_paginationViaApiWithLimits1_2_7() throws Exception {
        // KT-10 qua API: duyệt hết bằng limit 1, 2, 7 ra đúng tập và đúng thứ tự, không lặp, không sót
        UUID actorId = UUID.randomUUID();
        String targetType = "T" + System.nanoTime();
        Instant baseTime = Instant.parse("2026-10-25T00:00:00Z");

        List<AuditEntry> createdEntries = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            AuditEntry entry = createAndInsert(actorId, "ACT_PAGINATION", targetType, "tgt-" + i, baseTime.plusSeconds(i * 10), UUID.randomUUID(), Map.of(), null);
            createdEntries.add(0, entry); // Mới nhất trước
        }
        List<String> expectedIds = createdEntries.stream().map(e -> e.id().toString()).toList();

        for (int limit : List.of(1, 2, 7)) {
            List<String> collectedIds = new ArrayList<>();
            String cursor = null;
            int calls = 0;
            while (true) {
                // Trần số lần gọi: con trỏ sai (không tiến) sẽ lặp mãi, phải đỏ chứ không treo
                if (++calls > 100) {
                    throw new AssertionError("Duyệt trang không kết thúc sau 100 lần gọi: con trỏ không tiến");
                }
                var requestBuilder = get("/api/audit/entries")
                        .param("actorId", actorId.toString())
                        .param("targetType", targetType)
                        .param("limit", String.valueOf(limit));
                if (cursor != null) {
                    requestBuilder.param("cursor", cursor);
                }

                MvcResult result = mockMvc.perform(requestBuilder).andExpect(status().isOk()).andReturn();
                JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
                JsonNode items = body.path("data").path("items");

                for (JsonNode item : items) {
                    collectedIds.add(item.path("id").asText());
                }

                JsonNode nextCursorNode = body.path("data").path("nextCursor");
                if (nextCursorNode.isNull() || nextCursorNode.isMissingNode() || nextCursorNode.asText().isEmpty()) {
                    break;
                }
                cursor = nextCursorNode.asText();
            }

            assertThat(collectedIds).containsExactlyElementsOf(expectedIds);
        }
    }

    @Test
    void defaultPageSizeReturns50ItemsAndNextCursorWhen60RecordsExist() throws Exception {
        // Ghi 60 bản ghi của một người làm riêng, gọi không limit thì trả đúng 50 và có nextCursor
        UUID actorId = UUID.randomUUID();
        String targetType = "T" + System.nanoTime();
        Instant base = Instant.parse("2026-10-25T01:00:00Z");

        for (int i = 0; i < 60; i++) {
            createAndInsert(actorId, "ACT_DEFAULT_LIMIT", targetType, "tgt-" + i, base.plusSeconds(i), UUID.randomUUID(), Map.of(), null);
        }

        mockMvc.perform(get("/api/audit/entries")
                        .param("actorId", actorId.toString())
                        .param("targetType", targetType))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(50))
                .andExpect(jsonPath("$.data.nextCursor").isString());
    }

    @Test
    void kt11_errorHandlingAllReturnHttp400WithCorrectCodes() throws Exception {
        // KT-11: Mọi lỗi tham số đều HTTP 400 với code tương ứng
        String[][] cases = {
                {"limit", "0", "AUDIT_LIMIT_INVALID"},
                {"limit", "201", "AUDIT_LIMIT_INVALID"},
                {"limit", "-1", "AUDIT_LIMIT_INVALID"},
                {"limit", "abc", "AUDIT_PARAMETER_INVALID"},
                {"actorId", "khong-phai-uuid", "AUDIT_PARAMETER_INVALID"},
                {"from", "khong-phai-ngay", "AUDIT_PARAMETER_INVALID"},
                {"cursor", "!!!", "AUDIT_CURSOR_INVALID"}
        };

        for (String[] testCase : cases) {
            mockMvc.perform(get("/api/audit/entries").param(testCase[0], testCase[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(testCase[2]));
        }

        // from sau to -> AUDIT_TIME_RANGE_INVALID
        mockMvc.perform(get("/api/audit/entries")
                        .param("from", "2026-10-25T15:00:00Z")
                        .param("to", "2026-10-25T14:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUDIT_TIME_RANGE_INVALID"));

        // targetId không kèm targetType -> AUDIT_FILTER_INVALID
        mockMvc.perform(get("/api/audit/entries").param("targetId", "some-target-id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUDIT_FILTER_INVALID"));
    }

    @Test
    void kt12_detailEndpointReturnsAllFieldsAndValidatesId() throws Exception {
        // KT-12 chi tiết: GET /api/audit/entries/{id} trả đủ trường kể cả sourceEventId, details, traceId
        UUID actorId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        String targetType = "T" + System.nanoTime();
        Map<String, Object> details = Map.of("orderCode", "OD-12345", "amount", 500000);
        String traceId = "trace-detail-test";

        createAndInsert(actorId, "PAYMENT_CONFIRMED", targetType, "order-99", Instant.now(), entryId, details, traceId);

        mockMvc.perform(get("/api/audit/entries/" + entryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(entryId.toString()))
                .andExpect(jsonPath("$.data.sourceEventId").isString())
                .andExpect(jsonPath("$.data.traceId").value(traceId))
                .andExpect(jsonPath("$.data.details.orderCode").value("OD-12345"))
                .andExpect(jsonPath("$.data.details.amount").value(500000));

        // id không tồn tại -> 404 AUDIT_ENTRY_NOT_FOUND
        mockMvc.perform(get("/api/audit/entries/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUDIT_ENTRY_NOT_FOUND"));

        // id không phải UUID -> 400 AUDIT_PARAMETER_INVALID
        mockMvc.perform(get("/api/audit/entries/not-a-valid-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUDIT_PARAMETER_INVALID"));
    }

    @Test
    void kt14_noWriteEndpointsExistAndNonGetMethodsReturn405() throws Exception {
        // KT-14: Duyệt RequestMappingHandlerMapping cho mọi ánh xạ /api/audit và chỉ có GET
        requestMappingHandlerMapping.getHandlerMethods().forEach((info, method) -> {
            var patterns = info.getPatternValues();
            for (String pattern : patterns) {
                if (pattern.startsWith("/api/audit")) {
                    var methods = info.getMethodsCondition().getMethods();
                    assertThat(methods)
                            .withFailMessage("Đường dẫn %s chứa phương thức HTTP không phải GET: %s", pattern, methods)
                            .containsOnly(org.springframework.web.bind.annotation.RequestMethod.GET);
                }
            }
        });

        // POST, PUT, PATCH, DELETE vào cả hai đường dẫn đều trả HTTP 405
        String[] endpoints = {"/api/audit/entries", "/api/audit/entries/" + UUID.randomUUID()};
        for (String endpoint : endpoints) {
            mockMvc.perform(post(endpoint)).andExpect(status().isMethodNotAllowed());
            mockMvc.perform(put(endpoint)).andExpect(status().isMethodNotAllowed());
            mockMvc.perform(patch(endpoint)).andExpect(status().isMethodNotAllowed());
            mockMvc.perform(delete(endpoint)).andExpect(status().isMethodNotAllowed());
        }
    }

    @Test
    void lookupLogsDoNotContainDetailsContent(CapturedOutput output) throws Exception {
        // Log của các lời gọi tra cứu không chứa nội dung details đã ghi
        UUID actorId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        String sensitiveValue = "SECRET_SUPER_CONFIDENTIAL_CONTENT_123456";
        String targetType = "T" + System.nanoTime();

        createAndInsert(actorId, "SECRET_ACTION", targetType, "sec-1", Instant.now(), entryId, Map.of("key", sensitiveValue), "trace-sec");

        // Gọi GET list
        mockMvc.perform(get("/api/audit/entries").param("targetType", targetType)).andExpect(status().isOk());

        // Gọi GET detail
        mockMvc.perform(get("/api/audit/entries/" + entryId)).andExpect(status().isOk());

        // Kiểm tra CapturedOutput không chứa nội dung nhạy cảm của details
        assertThat(output.getAll()).doesNotContain(sensitiveValue);
    }
}
