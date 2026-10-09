package vn.t3nexus.lib.observability.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.AsyncAppender;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Đoạn cấu hình logback dùng chung ({@code observability-logback.xml}): console luôn ra chữ, file JSON cho Filebeat luôn được ghi;
 * cả hai qua bộ ghi bất đồng bộ. Ghi bất đồng bộ nên dòng log xuất hiện chậm hơn một chút: test chờ có giới hạn.
 */
class AsyncLogConfigTest {

    @Configuration
    static class Cfg {
    }

    private static final Logger log = LoggerFactory.getLogger("test.AsyncLog");

    static boolean eventually(CapturedOutput out, String marker) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (out.getAll().contains(marker)) return true;
            Thread.sleep(20);
        }
        return false;
    }

    @Nested
    @SpringBootTest(classes = Cfg.class, properties = {"spring.application.name=async-test", "logging.config=classpath:fragment-test-logback.xml"})
    @ExtendWith(OutputCaptureExtension.class)
    class Text {

        @Test
        void the_console_is_plain_text_through_a_bounded_async_appender(CapturedOutput out) throws Exception {
            log.info("async text line");

            assertThat(eventually(out, "async text line")).isTrue();
            String line = out.getAll().lines().filter(l -> l.contains("async text line")).findFirst().orElseThrow();
            assertThat(line).doesNotStartWith("{").contains("test.AsyncLog").contains("INFO");

            var context = (LoggerContext) LoggerFactory.getILoggerFactory();
            var appender = context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("ASYNC_CONSOLE");
            assertThat(appender).isInstanceOf(AsyncAppender.class);
            var async = (AsyncAppender) appender;
            assertThat(async.isNeverBlock()).isTrue();
            assertThat(async.getQueueSize()).isEqualTo(8192);
        }
    }

    @Nested
    @SpringBootTest(classes = Cfg.class, properties = {"spring.application.name=async-test",
            "logging.config=classpath:fragment-test-logback.xml", "logging.file.name=target/elk-test/async-test.json"})
    @ExtendWith(OutputCaptureExtension.class)
    class Elk {

        @Test
        void by_default_json_goes_to_a_file_for_elk_while_the_console_stays_plain_text(CapturedOutput out) throws Exception {
            log.info("elk file line");

            java.nio.file.Path file = java.nio.file.Path.of("target/elk-test/async-test.json");
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            String content = "";
            while (System.nanoTime() < deadline) {
                if (java.nio.file.Files.exists(file)) {
                    content = java.nio.file.Files.readString(file);
                    if (content.contains("elk file line")) break;
                }
                Thread.sleep(20);
            }
            String line = content.lines().filter(l -> l.contains("elk file line")).findFirst().orElseThrow();
            assertThat(line).startsWith("{").contains("\"log\":{\"level\":\"INFO\"").contains("\"name\":\"async-test\"");

            assertThat(eventually(out, "elk file line")).isTrue();
            String consoleLine = out.getAll().lines().filter(l -> l.contains("elk file line")).findFirst().orElseThrow();
            assertThat(consoleLine).doesNotStartWith("{");

            var context = (LoggerContext) LoggerFactory.getILoggerFactory();
            var appender = context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("ASYNC_FILE");
            assertThat(appender).isInstanceOf(AsyncAppender.class);
            assertThat(((AsyncAppender) appender).isNeverBlock()).isTrue();
        }
    }
}
