package vn.t3nexus.lib.events;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** KT-12: bên nhận bỏ qua trường lạ (tiến hoá chỉ thêm trường) và từ chối khung thiếu trường bắt buộc. */
class EventEnvelopeReaderTest {

    private static final String VALID = """
            {"eventId":"e-1","eventType":"SellerProfileApproved","aggregateType":"SellerProfile","aggregateId":"acc-1",
             "occurredAt":"2026-10-24T01:02:03Z","traceId":"trace-1","spanId":"span-1","schemaVersion":1,"payload":{"status":"APPROVED"}}""";

    // Bộ ánh xạ nghiêm: bật FAIL_ON_UNKNOWN_PROPERTIES để việc bỏ qua trường lạ phải do chính khung đảm nhận.
    private final ObjectMapper mapper = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private final EventEnvelopeReader reader = new EventEnvelopeReader(mapper);

    @Test
    void readsAValidEnvelope() {
        EventEnvelope e = reader.read(VALID);

        assertThat(e.eventId()).isEqualTo("e-1");
        assertThat(e.eventType()).isEqualTo("SellerProfileApproved");
        assertThat(e.aggregateType()).isEqualTo("SellerProfile");
        assertThat(e.aggregateId()).isEqualTo("acc-1");
        assertThat(e.occurredAt()).isEqualTo(Instant.parse("2026-10-24T01:02:03Z"));
        assertThat(e.traceId()).isEqualTo("trace-1");
        assertThat(e.spanId()).isEqualTo("span-1");
        assertThat(e.schemaVersion()).isEqualTo(1);
    }

    @Test
    void unknownTopLevelFieldsAreIgnored() {
        String withExtra = VALID.replace("\"schemaVersion\":1,", "\"schemaVersion\":1,\"futureField\":{\"a\":1},\"another\":\"x\",");

        assertThat(reader.read(withExtra).eventId()).isEqualTo("e-1");
    }

    @Test
    void unknownFieldsInsidePayloadAreKept() {
        String withExtra = VALID.replace("\"status\":\"APPROVED\"", "\"status\":\"APPROVED\",\"newField\":42");

        EventEnvelope e = reader.read(withExtra);

        assertThat(mapper.convertValue(e.payload(), Map.class)).containsEntry("newField", 42);
    }

    @Test
    void traceIdAndSpanIdMayBeAbsent() {
        EventEnvelope e = reader.read(VALID.replace("\"traceId\":\"trace-1\",\"spanId\":\"span-1\",", ""));

        assertThat(e.traceId()).isNull();
        assertThat(e.spanId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"eventId", "eventType", "aggregateType", "aggregateId", "occurredAt", "schemaVersion", "payload"})
    void missingRequiredFieldIsRejectedWithItsName(String field) {
        var tree = mapper.readTree(VALID);
        ((tools.jackson.databind.node.ObjectNode) tree).remove(field);

        assertThatThrownBy(() -> reader.read(mapper.writeValueAsString(tree)))
                .isInstanceOf(InvalidEventEnvelopeException.class)
                .hasMessageContaining(field);
    }

    @Test
    void blankRequiredStringIsRejected() {
        assertThatThrownBy(() -> reader.read(VALID.replace("\"eventId\":\"e-1\"", "\"eventId\":\"  \"")))
                .isInstanceOf(InvalidEventEnvelopeException.class).hasMessageContaining("eventId");
    }

    @Test
    void schemaVersionBelowOneIsRejected() {
        assertThatThrownBy(() -> reader.read(VALID.replace("\"schemaVersion\":1", "\"schemaVersion\":0")))
                .isInstanceOf(InvalidEventEnvelopeException.class).hasMessageContaining("schemaVersion");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not json", "{", "\"just a string\"", "42", "[1,2]", "null"})
    void nonObjectOrBrokenJsonIsRejected(String body) {
        assertThatThrownBy(() -> reader.read(body)).isInstanceOf(InvalidEventEnvelopeException.class);
    }

    @Test
    void rejectionMessageNeverEchoesTheBody() {
        String body = "{\"password\":\"hunter2-do-not-leak\"";   // JSON hỏng có bí mật

        assertThatThrownBy(() -> reader.read(body))
                .isInstanceOf(InvalidEventEnvelopeException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("hunter2"));
    }

    @Test
    void whatTheFactoryWritesTheReaderReadsBack() {
        EventEnvelopeFactory factory = new EventEnvelopeFactory(mapper, SensitiveFieldPolicy.strict());
        Instant at = Instant.parse("2026-10-24T05:06:07Z");
        EventEnvelope built = factory.create("e-9", "Product", "p-1", "ProductSnapshot", at, "t-9", "s-9", 2,
                Map.of("name", "Áo thun", "price", 150000));

        EventEnvelope read = reader.read(factory.toJson(built));

        assertThat(read.eventId()).isEqualTo("e-9");
        assertThat(read.occurredAt()).isEqualTo(at);
        assertThat(read.traceId()).isEqualTo("t-9");
        assertThat(read.spanId()).isEqualTo("s-9");
        assertThat(read.schemaVersion()).isEqualTo(2);
        assertThat(mapper.convertValue(read.payload(), Map.class)).containsEntry("name", "Áo thun").containsEntry("price", 150000);
    }
}
