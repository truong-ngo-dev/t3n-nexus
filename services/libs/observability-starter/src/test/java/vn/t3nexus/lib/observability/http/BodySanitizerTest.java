package vn.t3nexus.lib.observability.http;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BodySanitizerTest {

    private static final String JSON = "application/json";
    private static final String JWT = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1LTEifQ.c2lnbmF0dXJlLXNpZ25hdHVyZQ";
    private static final String BCRYPT = "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234";

    private final BodySanitizer sanitizer = new BodySanitizer();

    private String clean(String body) {
        return sanitizer.sanitize(body.getBytes(StandardCharsets.UTF_8), JSON, null);
    }

    @Test
    void masks_sensitive_field_names_at_any_depth_case_insensitively() {
        String out = clean("{\"email\":\"a@x.com\",\"Password\":\"Matkhau123\",\"nested\":{\"accessToken\":\"abc\",\"deep\":[{\"newPASSWORD\":\"p\"}]},\"otp\":\"123456\"}");

        assertThat(out).contains("\"email\":\"a@x.com\"");
        assertThat(out).doesNotContain("Matkhau123").doesNotContain("abc").doesNotContain("123456").doesNotContain("\"p\"");
        assertThat(out).contains("\"Password\":\"***\"").contains("\"accessToken\":\"***\"").contains("\"newPASSWORD\":\"***\"").contains("\"otp\":\"***\"");
    }

    @Test
    void does_not_over_mask_names_that_merely_contain_a_short_keyword() {
        String out = clean("{\"footprint\":\"x\",\"hotpot\":\"y\",\"pinned\":\"z\"}");

        assertThat(out).contains("\"footprint\":\"x\"").contains("\"hotpot\":\"y\"").contains("\"pinned\":\"z\"");
    }

    @Test
    void allow_list_keeps_only_the_listed_top_level_fields() {
        String out = sanitizer.sanitize("{\"email\":\"a@x.com\",\"password\":\"p\",\"role\":\"ADMIN\",\"future\":\"new\"}".getBytes(StandardCharsets.UTF_8),
                JSON, Set.of("email"));

        assertThat(out).isEqualTo("{\"email\":\"a@x.com\"}");
    }

    @Test
    void secret_patterns_are_scrubbed_inside_ordinary_values() {
        String out = clean("{\"note\":\"header was Bearer " + JWT + " ok\",\"jwt\":\"" + JWT + "\",\"hash\":\"" + BCRYPT + "\"}");

        assertThat(out).doesNotContain(JWT).doesNotContain(BCRYPT).doesNotContain("eyJ").contains("Bearer ***");
    }

    @Test
    void long_values_and_long_arrays_are_cut_with_a_marker() {
        String out = clean("{\"text\":\"" + "word ".repeat(100) + "\",\"items\":[1,2,3,4,5,6]}");

        assertThat(out).contains("…(cắt, tổng 500 ký tự)").contains("[1,2,3,\"…(+3 phần tử)\"]");
    }

    @Test
    void long_base64_like_strings_report_only_their_size() {
        String out = clean("{\"image\":\"" + "QUJD".repeat(100) + "\"}");

        assertThat(out).contains("[dữ liệu nhị phân 400 ký tự]").doesNotContain("QUJD");
    }

    @Test
    void non_json_content_types_report_only_type_and_size() {
        String out = sanitizer.sanitize(new byte[]{1, 2, 3, 4}, "application/octet-stream", null);

        assertThat(out).isEqualTo("[nội dung không ghi: application/octet-stream, 4 byte]");
    }

    @Test
    void plain_text_is_scrubbed_truncated_and_stripped_of_control_characters() {
        String out = sanitizer.sanitize(("Bearer " + JWT + "\nfake line\r\n" + "x".repeat(2000)).getBytes(StandardCharsets.UTF_8), "text/plain", null);

        assertThat(out).doesNotContain(JWT).doesNotContain("\n").doesNotContain("\r").endsWith("…(cắt)");
    }

    @Test
    void broken_or_truncated_json_falls_back_to_scrubbed_text() {
        String out = clean("{\"email\":\"a@x.com\",\"token\":\"" + JWT + "\", \"half");

        assertThat(out).doesNotContain(JWT).contains("a@x.com");
    }

    @Test
    void passwords_inside_truncated_or_broken_json_are_masked_in_the_text_fallback() {
        assertThat(clean("{\"email\":\"a@x.com\",\"password\":\"Matkhau123\", \"half")).doesNotContain("Matkhau123").contains("a@x.com");
        assertThat(clean("{\"user\": \"u\", \"accessToken\": \"abc.def\" ,")).doesNotContain("abc.def");
        assertThat(clean("{\"otp\":\"123456\",")).doesNotContain("123456");
        assertThat(clean("password=Matkhau123&email=a@x.com")).doesNotContain("Matkhau123");
        assertThat(clean("{\"password\":\"Matkhau123")).doesNotContain("Matkhau123");
    }

    @Test
    void unparseable_content_with_an_allow_list_logs_only_its_size() {
        String out = sanitizer.sanitize("{\"email\":\"a@x.com\",\"password\":\"p\", ".getBytes(StandardCharsets.UTF_8), JSON, Set.of("email"));

        assertThat(out).isEqualTo("[nội dung không phân tích được: 35 byte]");
    }

    @Test
    void json_that_reaches_the_byte_limit_is_marked_as_cut() {
        byte[] body = ("{\"a\":\"" + "x".repeat(5000)).substring(0, 4096).getBytes(StandardCharsets.UTF_8);

        assertThat(sanitizer.sanitize(body, JSON, null)).endsWith("…(đã cắt ở 4096 byte)");
    }

    @Test
    void empty_or_missing_body_yields_null() {
        assertThat(sanitizer.sanitize(null, JSON, null)).isNull();
        assertThat(sanitizer.sanitize(new byte[0], JSON, null)).isNull();
    }

    @Test
    void arbitrary_garbage_never_throws() {
        byte[] garbage = {(byte) 0xff, (byte) 0xfe, 0, 1, 2};

        assertThat(sanitizer.sanitize(garbage, JSON, null)).isNotNull();
        assertThat(sanitizer.sanitize(garbage, "text/plain", null)).isNotNull();
        assertThat(sanitizer.sanitize("[[[[[[[[[[".getBytes(StandardCharsets.UTF_8), JSON, null)).isNotNull();
    }

    @Test
    void scalar_json_and_null_values_pass_through() {
        assertThat(clean("42")).isEqualTo("42");
        assertThat(clean("{\"a\":null,\"b\":true,\"c\":1.5}")).isEqualTo("{\"a\":null,\"b\":true,\"c\":1.5}");
    }
}
