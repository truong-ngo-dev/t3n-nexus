package vn.t3nexus.storefrontwebbff;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import vn.t3nexus.storefrontwebbff.support.GatewayTestBase;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KT-25 (feature 06, `tech.md` T7, R20), nửa của BFF: mỗi yêu cầu có đúng một dòng hoàn tất ({@code http.request}), kể cả yêu cầu bị từ chối ở Spring
 * Security (401), mang cùng {@code traceId} với {@code traceparent} gửi xuống dịch vụ sau, và {@code client.ip} theo địa chỉ cổng đặt trong
 * {@code X-Client-Ip} (kết nối từ proxy tin cậy).
 */
class RequestLogTest extends GatewayTestBase {

    private static final Pattern TRACEPARENT = Pattern.compile("00-([0-9a-f]{32})-([0-9a-f]{16})-[0-9a-f]{2}");

    private final Logger logger = (Logger) LoggerFactory.getLogger("http.request");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attach() {
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
        logger.setLevel(null);
    }

    private List<ILoggingEvent> events(int expected) throws InterruptedException {
        for (int i = 0; i < 100 && appender.list.size() < expected; i++) Thread.sleep(50);
        Thread.sleep(100);    // để thấy cả dòng thừa nếu có
        return List.copyOf(appender.list);
    }

    @Test
    void a_proxied_request_logs_one_line_with_the_gateway_client_ip_and_the_trace_id_sent_downstream() throws Exception {
        IDENTITY.respond("GET", "/api/users/verify", 200, "application/json", "{}");

        client().get().uri("/api/identity/users/verify?token=abc").header("X-Client-Ip", "203.0.113.9").exchange().expectStatus().isOk();

        List<ILoggingEvent> events = events(1);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getFormattedMessage()).isEqualTo("GET /api/identity/users/verify 200");
        assertThat(events.get(0).getMDCPropertyMap()).containsEntry("client.ip", "203.0.113.9").containsEntry("http.response.status_code", "200");

        Matcher match = TRACEPARENT.matcher(String.valueOf(IDENTITY.last().header("traceparent")));
        assertThat(match.matches()).isTrue();
        assertThat(events.get(0).getMDCPropertyMap()).containsEntry("traceId", match.group(1));
    }

    @Test
    void a_request_rejected_by_spring_security_still_gets_its_line() throws Exception {
        client().get().uri("/api/customer/me").header("Accept", "application/json").exchange().expectStatus().isUnauthorized();

        List<ILoggingEvent> events = events(1);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getFormattedMessage()).isEqualTo("GET /api/customer/me 401");
        assertThat(events.get(0).getMDCPropertyMap()).containsKey("traceId");
    }
}
