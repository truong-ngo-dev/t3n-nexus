package vn.t3nexus.lib.observability.http;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import vn.t3nexus.lib.observability.logging.LogContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestLoggingFilterTest {

    private static final String REGISTER_BODY = "{\"email\":\"a@x.com\",\"password\":\"Matkhau123\",\"role\":\"ADMIN\"}";

    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final HttpLoggingProperties properties = new HttpLoggingProperties(true, 4096, 200, 3,
            java.util.Set.of(401, 403, 404, 405, 429), java.util.Set.of("otp", "pin", "pass", "cvc", "cvv"),
            List.of("password", "secret", "token", "authorization"), List.of("/actuator"));
    private final RequestLoggingFilter filter = new RequestLoggingFilter(properties, new BodySanitizer(properties.sanitizerOptions()));

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
        MDC.clear();
    }

    /** Controller giả, chỉ để có chú thích. */
    static class Controllers {
        @LogRequestFields("email")
        void register() {
        }

        void other() {
        }
    }

    private MockHttpServletRequest post(String path, String body) {
        var request = new MockHttpServletRequest("POST", path);
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.setRemoteAddr("10.0.0.7");
        return request;
    }

    /** Chuỗi lọc giả lập controller: đọc nội dung, đặt route và handler như DispatcherServlet, rồi đặt mã trạng thái. */
    private FilterChain chain(int status, String route, String handlerMethod) {
        return (req, res) -> {
            ((jakarta.servlet.http.HttpServletRequest) req).getInputStream().readAllBytes();
            if (route != null) req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, route);
            if (handlerMethod != null) {
                try {
                    req.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
                            new HandlerMethod(new Controllers(), Controllers.class.getDeclaredMethod(handlerMethod)));
                } catch (NoSuchMethodException e) {
                    throw new IllegalStateException(e);
                }
            }
            ((MockHttpServletResponse) res).setStatus(status);
        };
    }

    private ILoggingEvent only() {
        assertThat(appender.list).hasSize(1);
        return appender.list.get(0);
    }

    private static Map<String, String> mdc(ILoggingEvent event) {
        return event.getMDCPropertyMap();
    }

    @Test
    void a_successful_request_gets_one_info_line_with_the_summary_fields_and_no_body() throws Exception {
        var response = new MockHttpServletResponse();
        response.setHeader("Content-Length", "85");

        filter.doFilter(post("/register", REGISTER_BODY), response, chain(201, "/register", "register"));

        ILoggingEvent event = only();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage()).isEqualTo("POST /register 201");
        Map<String, String> fields = mdc(event);
        assertThat(fields).containsEntry("event.dataset", "http.request").containsEntry("http.request.method", "POST")
                .containsEntry("http.route", "/register").containsEntry("url.path", "/register")
                .containsEntry("http.response.status_code", "201").containsEntry("client.ip", "10.0.0.7")
                .containsEntry("http.request.body.bytes", String.valueOf(REGISTER_BODY.length()))
                .containsEntry("http.response.body.bytes", "85");
        assertThat(Long.parseLong(fields.get("event.duration"))).isPositive();
        assertThat(fields).doesNotContainKey("http.request.body.content");
        assertThat(event.getFormattedMessage() + fields).doesNotContain("Matkhau123");
    }

    @Test
    void a_bad_request_logs_the_sanitized_body_and_the_password_never_appears() throws Exception {
        filter.doFilter(post("/orders", "{\"sku\":\"A1\",\"password\":\"Matkhau123\"}"), new MockHttpServletResponse(),
                chain(400, "/orders", "other"));

        String body = mdc(only()).get("http.request.body.content");
        assertThat(body).contains("\"sku\":\"A1\"").contains("\"password\":\"***\"").doesNotContain("Matkhau123");
    }

    @Test
    void the_log_request_fields_annotation_keeps_only_the_listed_fields() throws Exception {
        filter.doFilter(post("/register", REGISTER_BODY), new MockHttpServletResponse(), chain(400, "/register", "register"));

        assertThat(mdc(only()).get("http.request.body.content")).isEqualTo("{\"email\":\"a@x.com\"}");
    }

    @Test
    void a_server_error_logs_the_body_even_when_the_chain_throws_and_the_exception_still_propagates() {
        var response = new MockHttpServletResponse();
        FilterChain failing = (req, res) -> {
            ((jakarta.servlet.http.HttpServletRequest) req).getInputStream().readAllBytes();
            throw new ServletException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(post("/orders", "{\"sku\":\"A1\"}"), response, failing))
                .isInstanceOf(ServletException.class).hasMessage("boom");

        Map<String, String> fields = mdc(only());
        assertThat(fields).containsEntry("http.response.status_code", "500");
        assertThat(fields.get("http.request.body.content")).isEqualTo("{\"sku\":\"A1\"}");
    }

    @Test
    void statuses_unrelated_to_the_callers_input_do_not_log_the_body() throws Exception {
        for (int status : new int[]{401, 403, 404, 405, 429}) {
            appender.list.clear();
            filter.doFilter(post("/orders", "{\"sku\":\"A1\"}"), new MockHttpServletResponse(), chain(status, null, null));

            assertThat(mdc(only())).containsEntry("http.response.status_code", String.valueOf(status))
                    .doesNotContainKey("http.request.body.content");
        }
    }

    @Test
    void a_request_rejected_before_any_controller_still_gets_a_line_without_a_route() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/secret"), new MockHttpServletResponse(), chain(401, null, null));

        assertThat(only().getFormattedMessage()).isEqualTo("GET /secret 401");
        assertThat(mdc(only())).doesNotContainKey("http.route").containsEntry("url.path", "/secret");
    }

    @Test
    void get_requests_are_not_buffered_and_never_log_a_body() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/orders"), new MockHttpServletResponse(), chain(500, "/orders", null));

        assertThat(mdc(only())).doesNotContainKey("http.request.body.content");
    }

    @Test
    void the_error_code_set_by_the_exception_handler_is_in_the_line_without_touching_the_context_afterwards() throws Exception {
        FilterChain chain = (req, res) -> {
            req.setAttribute("error.code", "EMAIL_TAKEN");
            ((MockHttpServletResponse) res).setStatus(409);
        };

        filter.doFilter(post("/register", REGISTER_BODY), new MockHttpServletResponse(), chain);

        assertThat(mdc(only())).containsEntry("error.code", "EMAIL_TAKEN");
        assertThat(MDC.get("error.code")).isNull(); // không để lại gì trong ngữ cảnh
    }

    @Test
    void the_filter_removes_only_the_keys_it_added_and_keeps_existing_context() throws Exception {
        MDC.put("traceId", "t-1");
        MDC.put("user.id", "u-1");

        filter.doFilter(post("/register", REGISTER_BODY), new MockHttpServletResponse(), chain(201, "/register", null));

        assertThat(MDC.get("traceId")).isEqualTo("t-1");
        assertThat(MDC.get("user.id")).isEqualTo("u-1");
        assertThat(MDC.get("event.dataset")).isNull();
        assertThat(MDC.get("http.response.status_code")).isNull();
        assertThat(MDC.get("event.duration")).isNull();
        assertThat(mdc(only())).containsEntry("traceId", "t-1").containsEntry("user.id", "u-1");
    }

    @Test
    void keys_the_use_case_puts_in_the_context_are_on_the_completion_line_and_gone_afterwards() throws Exception {
        MDC.put("traceId", "t-1");
        FilterChain chain = (req, res) -> {
            LogContext.put("orderId", "ORD-1");
            LogContext.user("u-9");
            LogContext.operation("placeOrder");
            ((MockHttpServletResponse) res).setStatus(201);
        };

        filter.doFilter(post("/orders", "{}"), new MockHttpServletResponse(), chain);

        assertThat(mdc(only())).containsEntry("labels.orderId", "ORD-1").containsEntry("user.id", "u-9")
                .containsEntry("labels.operation", "placeOrder").containsEntry("traceId", "t-1");
        assertThat(MDC.get("labels.orderId")).isNull();
        assertThat(MDC.get("user.id")).isNull();
        assertThat(MDC.get("labels.operation")).isNull();
        assertThat(MDC.get("traceId")).isEqualTo("t-1");
    }

    @Test
    void a_failing_sanitizer_never_breaks_the_request_and_the_line_is_still_written() throws Exception {
        var brokenSanitizer = new BodySanitizer() {
            @Override
            public String sanitize(byte[] body, String contentType, java.util.Set<String> allowFields) {
                throw new IllegalStateException("sanitizer bug");
            }
        };
        var resilient = new RequestLoggingFilter(properties, brokenSanitizer);

        resilient.doFilter(post("/orders", "{\"sku\":\"A1\"}"), new MockHttpServletResponse(), chain(400, "/orders", null));

        assertThat(mdc(only()).get("http.request.body.content")).isEqualTo(BodySanitizer.UNREADABLE);
    }

    @Test
    void the_received_line_is_written_at_debug_only_and_comes_before_the_completed_line() throws Exception {
        filter.doFilter(post("/register", REGISTER_BODY), new MockHttpServletResponse(), chain(201, "/register", null));
        assertThat(appender.list).hasSize(1);

        appender.list.clear();
        logger.setLevel(Level.DEBUG);
        filter.doFilter(post("/register", REGISTER_BODY), new MockHttpServletResponse(), chain(201, "/register", null));

        assertThat(appender.list).hasSize(2);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.DEBUG);
        assertThat(appender.list.get(0).getFormattedMessage()).isEqualTo("received POST /register");
        assertThat(mdc(appender.list.get(0))).doesNotContainKey("http.response.status_code");
        assertThat(appender.list.get(1).getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    void actuator_paths_are_not_logged() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health"), new MockHttpServletResponse(), chain(200, null, null));

        assertThat(appender.list).isEmpty();
    }

    @Test
    void the_request_body_remains_readable_by_the_controller() throws Exception {
        var seen = new String[1];
        FilterChain reading = (req, res) -> seen[0] = new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        filter.doFilter(post("/register", REGISTER_BODY), new MockHttpServletResponse(), reading);

        assertThat(seen[0]).isEqualTo(REGISTER_BODY);
    }

}
