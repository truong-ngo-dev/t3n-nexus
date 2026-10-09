package vn.t3nexus.oauth2.presentation.user_account;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Đọc log JSON thật của ứng dụng (file mà Filebeat đọc; console luôn ra chữ) khi gọi {@code POST /register}: dòng hoàn tất của mỗi yêu cầu, nội dung chỉ khi lỗi và chỉ có email,
 * mật khẩu không bao giờ xuất hiện, {@code traceId} trong phản hồi lỗi khớp {@code trace.id} của log (feature 07).
 * <br>Cần cùng hạ tầng như các test API khác. Tài khoản thử bị xóa sau mỗi test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "logging.file.name=" + RequestLoggingIntegrationTest.FILE)
class RequestLoggingIntegrationTest {

    static final String FILE = "target/request-logging-test/oauth2-service.json";

    private static final String PASSWORD = "Matkhau123";

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;

    /** Xóa file của lần chạy trước, trước khi Spring khởi động ghi log. */
    @BeforeAll
    static void deleteOldFile() throws IOException {
        Files.deleteIfExists(Path.of(FILE));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from user_accounts where email like 'log-test-%@example.com'");
    }

    private HttpResponse<String> register(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String email() {
        return "log-test-" + UUID.randomUUID() + "@example.com";
    }

    private String logFile() {
        try {
            Path file = Path.of(FILE);
            return Files.exists(file) ? Files.readString(file) : "";
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Các dòng log JSON trong file, theo thứ tự. */
    private List<JsonNode> jsonLines() {
        List<JsonNode> lines = new ArrayList<>();
        for (String line : logFile().split("\\R")) {
            if (line.startsWith("{")) {
                try {
                    lines.add(json.readTree(line));
                } catch (RuntimeException notJson) {
                    // dòng không phải JSON hợp lệ (ví dụ stack trace cắt dở) bị bỏ qua
                }
            }
        }
        return lines;
    }

    /** Log được ghi bất đồng bộ nên dòng có thể tới chậm một chút: chờ có giới hạn. */
    private JsonNode completionLine(int status) throws InterruptedException {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
        while (true) {
            var found = jsonLines().stream()
                    .filter(l -> "http.request".equals(l.path("event").path("dataset").asString()))
                    .filter(l -> l.path("http").path("response").path("status_code").asInt() == status)
                    .reduce((first, second) -> second);
            if (found.isPresent()) return found.get();
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Không có dòng hoàn tất với status " + status + ":\n" + logFile());
            }
            Thread.sleep(20);
        }
    }

    @Test
    void a_successful_registration_logs_one_completion_line_without_a_body_and_with_the_new_user_id() throws Exception {
        HttpResponse<String> response = register("{\"email\":\"" + email() + "\",\"password\":\"" + PASSWORD + "\"}");
        String userAccountId = json.readTree(response.body()).path("data").path("userAccountId").asString();

        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode line = completionLine(201);
        assertThat(line.path("message").asString()).isEqualTo("POST /register 201");
        assertThat(line.path("log").path("level").asString()).isEqualTo("INFO");
        assertThat(line.path("http").path("request").path("method").asString()).isEqualTo("POST");
        assertThat(line.path("http").path("route").asString()).isEqualTo("/register");
        assertThat(line.path("event").path("duration").asLong()).isPositive();
        assertThat(line.path("user").path("id").asString()).isEqualTo(userAccountId);
        assertThat(line.path("http").path("request").path("body").has("content")).isFalse();
        assertThat(line.toString()).doesNotContain(PASSWORD);

        // dòng của use case cũng mang cùng user.id (dòng hoàn tất đã có thì dòng trước nó cũng đã được ghi)
        JsonNode appLine = jsonLines().stream()
                .filter(l -> l.path("message").asString().startsWith("registered userAccountId=")).findFirst().orElseThrow();
        assertThat(appLine.path("user").path("id").asString()).isEqualTo(userAccountId);
        assertThat(appLine.path("trace.id").asString()).isEqualTo(line.path("trace.id").asString());
        assertThat(logFile()).doesNotContain(PASSWORD);
    }

    @Test
    void a_duplicate_email_logs_the_error_code_and_only_the_email_from_the_body() throws Exception {
        String email = email();
        register("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}");

        HttpResponse<String> response = register("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}");

        assertThat(response.statusCode()).isEqualTo(409);
        JsonNode line = completionLine(409);
        assertThat(line.path("error").path("code").asString()).isEqualTo("EMAIL_TAKEN");
        assertThat(line.path("http").path("request").path("body").path("content").asString())
                .isEqualTo("{\"email\":\"" + email + "\"}");
        assertThat(logFile()).doesNotContain(PASSWORD);
    }

    @Test
    void an_invalid_password_is_a_400_with_the_email_only_in_the_logged_body() throws Exception {
        String email = email();

        HttpResponse<String> response = register("{\"email\":\"" + email + "\",\"password\":\"short1\"}");

        assertThat(response.statusCode()).isEqualTo(400);
        JsonNode line = completionLine(400);
        assertThat(line.path("error").path("code").asString()).isEqualTo("PASSWORD_INVALID");
        assertThat(line.path("http").path("request").path("body").path("content").asString()).isEqualTo("{\"email\":\"" + email + "\"}");
        assertThat(logFile()).doesNotContain("short1");
    }

    @Test
    void broken_json_is_a_400_malformed_request_and_the_password_in_the_broken_body_never_reaches_the_log() throws Exception {
        HttpResponse<String> response = register("{\"email\":\"" + email() + "\",\"password\":\"" + PASSWORD + "\", ");

        assertThat(response.statusCode()).isEqualTo(400);
        JsonNode body = json.readTree(response.body());
        assertThat(body.path("code").asString()).isEqualTo("MALFORMED_REQUEST");

        JsonNode line = completionLine(400);
        assertThat(line.path("error").path("code").asString()).isEqualTo("MALFORMED_REQUEST");
        // có luật trường được phép nhưng nội dung không phân tích được: chỉ ghi kích thước
        assertThat(line.path("http").path("request").path("body").path("content").asString()).startsWith("[nội dung không phân tích được:");
        assertThat(logFile()).doesNotContain(PASSWORD);
    }

    @Test
    void the_trace_id_in_an_error_response_matches_the_trace_id_of_the_log_lines() throws Exception {
        HttpResponse<String> response = register("{\"email\":\"" + email() + "\",\"password\":\"short1\"}");

        String traceInResponse = json.readTree(response.body()).path("traceId").asString();
        assertThat(traceInResponse).isNotBlank();
        assertThat(completionLine(400).path("trace.id").asString()).isEqualTo(traceInResponse);
    }
}
