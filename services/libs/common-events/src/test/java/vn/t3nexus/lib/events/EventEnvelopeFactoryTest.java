package vn.t3nexus.lib.events;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** KT-11: tên trường nhạy cảm bị từ chối ở dựng khung, ở mọi độ sâu. Kèm kiểm trường bắt buộc của bộ dựng. */
class EventEnvelopeFactoryTest {

    private static final Instant AT = Instant.parse("2026-10-24T01:02:03Z");
    private static final String SECRET_VALUE = "hunter2-do-not-leak";

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final EventEnvelopeFactory strict = new EventEnvelopeFactory(mapper, SensitiveFieldPolicy.strict());
    private final EventEnvelopeFactory secretsOnly = new EventEnvelopeFactory(mapper, SensitiveFieldPolicy.secretsOnly());

    private EventEnvelope build(EventEnvelopeFactory factory, Object payload) {
        return factory.create("0198f6a0-0000-7000-8000-000000000001", "SellerProfile", "acc-1",
                "SellerProfileApproved", AT, "trace-1", "span-1", 1, payload);
    }

    @Test
    void cleanPayloadBuildsEnvelopeWithAllFields() {
        EventEnvelope e = build(strict, Map.of("status", "APPROVED", "version", 3));

        assertThat(e.eventId()).isEqualTo("0198f6a0-0000-7000-8000-000000000001");
        assertThat(e.aggregateType()).isEqualTo("SellerProfile");
        assertThat(e.aggregateId()).isEqualTo("acc-1");
        assertThat(e.eventType()).isEqualTo("SellerProfileApproved");
        assertThat(e.occurredAt()).isEqualTo(AT);
        assertThat(e.traceId()).isEqualTo("trace-1");
        assertThat(e.spanId()).isEqualTo("span-1");
        assertThat(e.schemaVersion()).isEqualTo(1);
    }

    @Test
    void jsonHasNoSchemaWrapperAndCarriesSpanId() {
        JsonNode json = mapper.readTree(strict.toJson(build(strict, Map.of("status", "APPROVED"))));

        assertThat(json.has("schema")).isFalse();
        assertThat(json.get("spanId").asString()).isEqualTo("span-1");
        assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(json.get("payload").get("status").asString()).isEqualTo("APPROVED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", "passwordHash", "token", "accessToken", "refreshToken", "otp", "secret",
            "authorization", "cardNumber", "apiKey"})
    void secretNamesAreRejectedByEveryPolicy(String name) {
        Map<String, Object> payload = Map.of(name, SECRET_VALUE);

        for (EventEnvelopeFactory f : List.of(strict, secretsOnly)) {
            assertThatThrownBy(() -> build(f, payload))
                    .isInstanceOf(SensitiveFieldException.class)
                    .hasMessageContaining(name);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"identityNumber", "nationalId", "taxCode", "address", "phone", "phoneNumber", "email"})
    void personalIdentifierNamesAreRejectedByStrictPolicyOnly(String name) {
        Map<String, Object> payload = Map.of(name, "some-value");

        assertThatThrownBy(() -> build(strict, payload))
                .isInstanceOf(SensitiveFieldException.class)
                .hasMessageContaining(name);
        assertThat(build(secretsOnly, payload).payload()).isNotNull();
    }

    @Test
    void nonePolicyChecksNothing() {
        EventEnvelopeFactory none = new EventEnvelopeFactory(mapper, SensitiveFieldPolicy.none());

        assertThat(build(none, Map.of("password", "x", "email", "a@b.c"))).isNotNull();
    }

    @Test
    void nestedFieldIsRejectedAndMessageGivesPath() {
        Map<String, Object> payload = Map.of("seller", Map.of("legal", Map.of("taxCode", "0123456789")));

        assertThatThrownBy(() -> build(strict, payload))
                .isInstanceOf(SensitiveFieldException.class)
                .hasMessageContaining("payload.seller.legal.taxCode");
    }

    @Test
    void fieldInsideArrayElementIsRejectedAndMessageGivesIndex() {
        Map<String, Object> payload = Map.of("items", List.of(Map.of("sku", "a"), Map.of("accessToken", SECRET_VALUE)));

        assertThatThrownBy(() -> build(strict, payload))
                .isInstanceOf(SensitiveFieldException.class)
                .hasMessageContaining("payload.items[1].accessToken");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Password", "PASSWORD", "access_token", "Access-Token", "ACCESS_TOKEN", "Tax_Code"})
    void namesMatchIgnoringCaseAndSeparators(String name) {
        assertThatThrownBy(() -> build(strict, Map.of(name, "x")))
                .isInstanceOf(SensitiveFieldException.class);
    }

    @Test
    void messageNeverContainsTheValue() {
        assertThatThrownBy(() -> build(strict, Map.of("password", SECRET_VALUE)))
                .isInstanceOf(SensitiveFieldException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(SECRET_VALUE));
    }

    @Test
    void valuesAreNotInspectedOnlyNames() {
        // Giá trị trông giống mật khẩu nhưng tên trường sạch: chấp nhận (thư viện chỉ kiểm tên).
        assertThat(build(strict, Map.of("note", "password=abc"))).isNotNull();
    }

    @Test
    void nameThatMerelyContainsASensitiveWordIsAccepted() {
        // "tokenCount", "addressId" không phải trường nhạy cảm; so khớp theo tên đầy đủ, không theo chuỗi con.
        assertThat(build(strict, Map.of("tokenCount", 2, "addressId", "a-1", "emailVerified", true))).isNotNull();
    }

    @Test
    void requiredFieldsAreEnforced() {
        assertThatThrownBy(() -> strict.create(" ", "A", "id", "T", AT, null, null, 1, Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("eventId");
        assertThatThrownBy(() -> strict.create("e", null, "id", "T", AT, null, null, 1, Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("aggregateType");
        assertThatThrownBy(() -> strict.create("e", "A", "", "T", AT, null, null, 1, Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("aggregateId");
        assertThatThrownBy(() -> strict.create("e", "A", "id", null, AT, null, null, 1, Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("eventType");
        assertThatThrownBy(() -> strict.create("e", "A", "id", "T", null, null, null, 1, Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("occurredAt");
        assertThatThrownBy(() -> strict.create("e", "A", "id", "T", AT, null, null, 0, Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("schemaVersion");
        assertThatThrownBy(() -> strict.create("e", "A", "id", "T", AT, null, null, 1, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("payload");
    }

    @Test
    void traceIdAndSpanIdMayBeAbsent() {
        EventEnvelope e = strict.create("e", "A", "id", "T", AT, null, null, 1, Map.of("k", "v"));

        assertThat(e.traceId()).isNull();
        assertThat(e.spanId()).isNull();
    }
}
