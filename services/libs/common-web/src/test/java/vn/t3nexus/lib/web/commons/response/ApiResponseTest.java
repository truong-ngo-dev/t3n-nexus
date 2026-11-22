package vn.t3nexus.lib.web.commons.response;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the JSON shape of {@link ApiResponse}: what is present on success, what on failure, and that absent fields are omitted.
 */
class ApiResponseTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    private JsonNode json(ApiResponse<?> response) {
        return mapper.valueToTree(response);
    }

    @Test
    void success_carries_code_OK_and_no_error_fields() {
        JsonNode node = json(ApiResponse.ok(Map.of("id", "1")));

        assertThat(node.get("code").asString()).isEqualTo("OK");
        assertThat(node.get("data").get("id").asString()).isEqualTo("1");
        assertThat(node.has("error")).isFalse();
        assertThat(node.has("timestamp")).isFalse();
        assertThat(node.has("traceId")).isFalse();
    }

    @Test
    void failure_carries_code_and_an_iso_8601_utc_timestamp() {
        Instant before = Instant.now();
        JsonNode node = json(ApiResponse.error("Shop name is already in use", "SHOP_NAME_TAKEN"));

        assertThat(node.get("code").asString()).isEqualTo("SHOP_NAME_TAKEN");
        assertThat(node.has("error")).isFalse();
        String timestamp = node.get("timestamp").asString();
        assertThat(timestamp).endsWith("Z");
        assertThat(Instant.parse(timestamp)).isBetween(before.minusSeconds(1), Instant.now().plusSeconds(1));
    }

    @Test
    void the_six_component_constructor_still_works_and_leaves_error_and_timestamp_absent() {
        JsonNode node = json(new ApiResponse<>(false, null, "msg", List.of("shopName"), "SELLER_FIELD_INVALID", "trace-1"));

        assertThat(node.get("code").asString()).isEqualTo("SELLER_FIELD_INVALID");
        assertThat(node.get("traceId").asString()).isEqualTo("trace-1");
        assertThat(node.has("error")).isFalse();
        assertThat(node.has("timestamp")).isFalse();
    }

    @Test
    void error_serializes_by_runtime_type_as_string_object_or_array() {
        record Detail(String field, String code) {
        }
        Instant now = Instant.now();

        JsonNode asString = json(new ApiResponse<>(false, null, "m", List.of(), "C", null, "note", now));
        JsonNode asObject = json(new ApiResponse<>(false, null, "m", List.of(), "C", null, Map.of("currentStatus", "APPROVED"), now));
        JsonNode asArray = json(new ApiResponse<>(false, null, "m", List.of(), "C", null, List.of(new Detail("shopName", "TOO_SHORT")), now));

        assertThat(asString.get("error").isString()).isTrue();
        assertThat(asObject.get("error").get("currentStatus").asString()).isEqualTo("APPROVED");
        assertThat(asArray.get("error").get(0).get("field").asString()).isEqualTo("shopName");
        assertThat(asArray.get("error").get(0).get("code").asString()).isEqualTo("TOO_SHORT");
    }
}
