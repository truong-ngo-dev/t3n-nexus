package vn.t3nexus.oauth2.presentation.user_account;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hợp đồng của {@code POST /register} (bước 9): gọi thật vào ứng dụng đang chạy trên cổng ngẫu nhiên, đối chiếu
 * {@code api.yaml}. KT-02 và phần 400/409 của KT-09.
 * <br>Cần các dịch vụ hạ tầng như khi chạy ứng dụng (Postgres, Redis, Kafka đang bật theo {@code application.properties}).
 * Tài khoản test có email {@code api-test-*@example.com} và bị xóa sau mỗi test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserAccountApiTest {

    private static final String PASSWORD = "Matkhau123";

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from user_accounts where email like 'api-test-%@example.com'");
    }

    @Test
    void valid_registration_returns_201_with_a_uuid_v7_id_and_stores_an_active_user_role_account() throws Exception {
        String email = randomEmail();

        Reply reply = register(Map.of("email", email, "password", PASSWORD));

        assertThat(reply.status).isEqualTo(201);
        assertThat(reply.body.path("success").asBoolean()).isTrue();
        UUID id = UUID.fromString(reply.body.path("data").path("userAccountId").asString());
        assertThat(id.version()).isEqualTo(7);
        assertThat(reply.raw).doesNotContain(PASSWORD);
        Map<String, Object> row = jdbc.queryForMap("select role, status from user_accounts where id = ?", id);
        assertThat(row).containsEntry("role", "USER").containsEntry("status", "ACTIVE");
    }

    /** KT-02: vai không phải đầu vào; trường lạ gửi kèm bị bỏ qua. */
    @Test
    void a_role_sent_in_the_request_is_ignored() throws Exception {
        String email = randomEmail();

        Reply reply = register(Map.of("email", email, "password", PASSWORD, "role", "ADMIN", "fullName", "Ai Do"));

        assertThat(reply.status).isEqualTo(201);
        assertThat(jdbc.queryForObject("select role from user_accounts where email = ?", String.class, email))
                .isEqualTo("USER");
    }

    @Test
    void email_in_a_different_case_is_stored_normalized_and_the_same_email_again_is_409_email_taken() throws Exception {
        String email = randomEmail();
        assertThat(register(Map.of("email", "  " + email.toUpperCase() + " ", "password", PASSWORD)).status).isEqualTo(201);
        assertThat(jdbc.queryForObject("select count(*) from user_accounts where email = ?", Integer.class, email))
                .isEqualTo(1);

        Reply again = register(Map.of("email", email, "password", PASSWORD));

        assertError(again, 409, "EMAIL_TAKEN");
    }

    @Test
    void invalid_input_is_400_with_a_stable_code_and_a_vietnamese_message() throws Exception {
        assertError(register(Map.of("email", "khong-phai-email", "password", PASSWORD)), 400, "EMAIL_INVALID");
        assertError(register(Map.of("email", "boss@t3nexus.com.vn", "password", PASSWORD)), 400, "EMAIL_RESERVED");
        assertError(register(Map.of("email", randomEmail(), "password", "ngan1")), 400, "PASSWORD_INVALID");
        assertError(register(Map.of("email", randomEmail(), "password", "mat khau 123")), 400, "PASSWORD_INVALID");
        assertError(register(Map.of("email", randomEmail())), 400, "VALIDATION_FAILED");
    }

    @Test
    void a_failed_registration_leaves_no_account() throws Exception {
        String email = randomEmail();

        register(Map.of("email", email, "password", "ngan1"));

        assertThat(jdbc.queryForObject("select count(*) from user_accounts where email = ?", Integer.class, email))
                .isZero();
    }

    private void assertError(Reply reply, int status, String code) {
        assertThat(reply.status).as(reply.raw).isEqualTo(status);
        assertThat(reply.body.path("success").asBoolean()).isFalse();
        assertThat(reply.body.path("code").asString()).as(reply.raw).isEqualTo(code);
        assertThat(reply.body.path("message").asString()).isNotBlank();
        assertThat(reply.raw).doesNotContain("Exception").doesNotContain(PASSWORD);
    }

    private Reply register(Map<String, String> body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(), response.body(), json.readTree(response.body()));
    }

    private static String randomEmail() {
        return "api-test-" + UUID.randomUUID() + "@example.com";
    }

    private record Reply(int status, String raw, JsonNode body) {
    }
}
