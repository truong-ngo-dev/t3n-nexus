package vn.t3nexus.lib.observability.logging;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;
import vn.t3nexus.lib.observability.ObservabilityEnvironmentPostProcessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cờ gỡ lỗi {@code observability.logging.reveal-secrets}: ở profile dev thì message trong file JSON giữ nguyên token; ở profile khác thì
 * ứng dụng từ chối khởi động. Việc che nội dung yêu cầu HTTP do {@code BodySanitizer} đảm nhiệm và có test riêng.
 */
@SpringBootTest(classes = RevealSecretsTest.Cfg.class, properties = {"spring.application.name=reveal-test",
        "spring.profiles.active=dev", "observability.logging.reveal-secrets=true",
        "logging.file.name=" + RevealSecretsTest.FILE})
class RevealSecretsTest {

    static final String FILE = "target/reveal-secrets-test/reveal-test.json";

    @Configuration
    static class Cfg {
    }

    private static final Logger log = LoggerFactory.getLogger("test.Reveal");
    private static final String JWT = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1LTEifQ.c2lnbmF0dXJlLXNpZ25hdHVyZQ";

    @BeforeAll
    static void deleteOldFile() throws IOException {
        Files.deleteIfExists(Path.of(FILE));
    }

    @Test
    void with_the_flag_on_in_dev_the_token_stays_readable_in_the_json_file() throws Exception {
        log.warn("debug token {}", JWT);

        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        String content = "";
        while (System.nanoTime() < deadline && !content.contains("debug token")) {
            Thread.sleep(20);
            content = Files.exists(Path.of(FILE)) ? Files.readString(Path.of(FILE)) : "";
        }
        assertThat(content).contains(JWT);
    }

    @Test
    void the_flag_outside_the_dev_profile_refuses_to_start() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("observability.logging.reveal-secrets", "true")
                .withProperty("spring.profiles.active", "prod");

        assertThatThrownBy(() -> new ObservabilityEnvironmentPostProcessor().postProcessEnvironment(env, new SpringApplication()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("profile dev");
    }
}
