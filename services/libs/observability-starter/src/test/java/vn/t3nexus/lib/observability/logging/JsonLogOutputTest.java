package vn.t3nexus.lib.observability.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Đọc đầu ra JSON thật của log, tức là file mà Filebeat đọc: mặc định của starter (định dạng ECS, tên trace, bộ quét bí mật) và cách khóa
 * MDC ra thành trường. Console luôn ra chữ nên không dùng để kiểm JSON. Log ghi bất đồng bộ nên test chờ có giới hạn.
 */
@SpringBootTest(classes = JsonLogOutputTest.Cfg.class, properties = {"spring.application.name=obs-test",
        "logging.file.name=" + JsonLogOutputTest.FILE})
class JsonLogOutputTest {

    static final String FILE = "target/json-log-output-test/obs-test.json";

    @Configuration
    static class Cfg {
    }

    private static final Logger log = LoggerFactory.getLogger("test.JsonLog");
    private static final String JWT = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1LTEifQ.c2lnbmF0dXJlLXNpZ25hdHVyZQ";
    private static final String BCRYPT = "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234";

    private final JsonMapper mapper = JsonMapper.builder().build();

    /** Xóa file của lần chạy trước, trước khi Spring khởi động ghi log. */
    @BeforeAll
    static void deleteOldFile() throws IOException {
        Files.deleteIfExists(Path.of(FILE));
    }

    @AfterEach
    void cleanUp() {
        MDC.clear();
    }

    private String fileContent() throws IOException {
        Path file = Path.of(FILE);
        return Files.exists(file) ? Files.readString(file) : "";
    }

    /** Chờ dòng JSON chứa dấu hiệu xuất hiện trong file. */
    private JsonNode lineContaining(String marker) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            for (String line : fileContent().split("\\R")) {
                if (line.startsWith("{") && line.contains(marker)) return mapper.readTree(line);
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Không có dòng JSON chứa '" + marker + "' trong file:\n" + fileContent());
    }

    @Test
    void mdc_context_becomes_fields_and_classic_logging_calls_are_supported() throws Exception {
        MDC.put("traceId", "abc123");
        MDC.put("spanId", "def456");
        LogContext.put("orderId", "ORD-7731");
        LogContext.user("u-1");
        LogContext.operation("exportReport");

        log.info("done {} items", 3);

        JsonNode json = lineContaining("done 3 items");
        assertThat(json.path("log").path("level").asString()).isEqualTo("INFO");
        assertThat(json.path("log").path("logger").asString()).isEqualTo("test.JsonLog");
        assertThat(json.path("message").asString()).isEqualTo("done 3 items");
        assertThat(json.path("service").path("name").asString()).isEqualTo("obs-test");
        assertThat(json.path("trace.id").asString()).isEqualTo("abc123");
        assertThat(json.path("span.id").asString()).isEqualTo("def456");
        assertThat(json.path("labels").path("orderId").asString()).isEqualTo("ORD-7731");
        assertThat(json.path("labels").path("operation").asString()).isEqualTo("exportReport");
        assertThat(json.path("user").path("id").asString()).isEqualTo("u-1");
        assertThat(json.has("@timestamp")).isTrue();
    }

    @Test
    void an_exception_is_written_as_error_fields_with_the_stack_trace() throws Exception {
        log.error("export failed", new IllegalStateException("kaboom"));

        JsonNode json = lineContaining("export failed");
        assertThat(json.path("log").path("level").asString()).isEqualTo("ERROR");
        assertThat(json.path("error").path("type").asString()).isEqualTo("java.lang.IllegalStateException");
        assertThat(json.path("error").path("message").asString()).isEqualTo("kaboom");
        assertThat(json.path("error").path("stack_trace").asString()).contains("IllegalStateException").contains("JsonLogOutputTest");
    }

    @Test
    void tokens_and_hashes_left_in_a_message_or_stack_trace_are_scrubbed_in_the_json_file() throws Exception {
        log.warn("auth header was Bearer {} and hash {}", JWT, BCRYPT);
        log.error("leaky failure", new IllegalStateException("token=Bearer " + JWT + " hash " + BCRYPT));

        JsonNode message = lineContaining("auth header was");
        assertThat(message.path("message").asString()).doesNotContain(JWT).doesNotContain(BCRYPT).contains("Bearer ***");
        JsonNode error = lineContaining("leaky failure");
        assertThat(error.path("error").path("stack_trace").asString()).doesNotContain(JWT).doesNotContain(BCRYPT);
        // Chỉ file được quét; console chữ không được quét (xem deferred.md của feature 07)
        assertThat(fileContent()).doesNotContain(JWT).doesNotContain(BCRYPT);
    }

    @Test
    void sl4j_key_value_pairs_are_also_written_as_fields() throws Exception {
        log.atInfo().addKeyValue("shard", "s1").log("with key value");

        assertThat(lineContaining("with key value").path("shard").asString()).isEqualTo("s1");
    }
}
