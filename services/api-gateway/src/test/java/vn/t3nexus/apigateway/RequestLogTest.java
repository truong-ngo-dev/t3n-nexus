package vn.t3nexus.apigateway;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import vn.t3nexus.apigateway.support.GatewayTestBase;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KT-25 (feature 06, `tech.md` T7, R20), nửa của cổng: mỗi yêu cầu qua {@code api-gateway} có đúng một dòng hoàn tất ({@code http.request}, INFO)
 * kể cả khi bị từ chối trước khi tới dịch vụ nào (host lạ, 404), mang cùng {@code traceId} với tiêu đề {@code traceparent} mà dịch vụ sau nhận
 * (để nối log hai bên), và {@code client.ip} là địa chỉ kết nối chứ không phải tiêu đề do client gửi. Nửa còn lại (dịch vụ sau ghi dòng cùng mã
 * truy vết, đọc log thật) kiểm ở bước chạy thật.
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

    /** Dòng log được ghi lúc kết thúc, có thể sau khi client đã nhận phản hồi: chờ tối đa vài giây. */
    private List<ILoggingEvent> events(int expected) throws InterruptedException {
        for (int i = 0; i < 100 && appender.list.size() < expected; i++) Thread.sleep(50);
        Thread.sleep(100);    // để thấy cả dòng thừa nếu có
        return List.copyOf(appender.list);
    }

    @Test
    void a_proxied_request_logs_one_completion_line_whose_trace_id_matches_the_traceparent_sent_downstream() throws Exception {
        OAUTH2.respond("GET", "/login", 200, "text/plain", "ok");

        get(AUTH_HOST, "/login").header("X-Client-Ip", "9.9.9.9").header("X-Forwarded-For", "8.8.8.8").exchange().expectStatus().isOk();

        List<ILoggingEvent> events = events(1);
        assertThat(events).hasSize(1);
        ILoggingEvent event = events.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage()).isEqualTo("GET /login 200");
        Map<String, String> fields = event.getMDCPropertyMap();
        assertThat(fields).containsEntry("event.dataset", "http.request").containsEntry("http.request.method", "GET")
                .containsEntry("url.path", "/login").containsEntry("http.response.status_code", "200")
                .containsEntry("client.ip", "127.0.0.1");
        assertThat(Long.parseLong(fields.get("event.duration"))).isPositive();

        String traceparent = OAUTH2.last().header("traceparent");
        assertThat(traceparent).as("cổng truyền ngữ cảnh truy vết xuống dịch vụ sau").isNotNull();
        Matcher match = TRACEPARENT.matcher(traceparent);
        assertThat(match.matches()).as(traceparent).isTrue();
        assertThat(fields).containsEntry("traceId", match.group(1));
    }

    @Test
    void a_request_rejected_before_any_route_still_gets_its_line() throws Exception {
        get("evil.example", "/anything").exchange().expectStatus().isNotFound();

        List<ILoggingEvent> events = events(1);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getFormattedMessage()).isEqualTo("GET /anything 404");
        assertThat(events.get(0).getMDCPropertyMap()).containsKey("traceId");
    }
}
