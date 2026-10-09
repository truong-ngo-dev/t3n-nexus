package vn.t3nexus.lib.web.commons.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.exception.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class GlobalExceptionHandlerTest {

    enum TestError implements ErrorCode {
        EMAIL_TAKEN("1", "Email này đã được dùng", "k", 409),
        BROKEN("2", "Lỗi nghiệp vụ nặng", "k", 500);

        private final String code;
        private final String message;
        private final String key;
        private final int status;

        TestError(String code, String message, String key, int status) {
            this.code = code;
            this.message = message;
            this.key = key;
            this.status = status;
        }

        @Override public String code() { return code; }
        @Override public String defaultMessage() { return message; }
        @Override public String messageKey() { return key; }
        @Override public int httpStatus() { return status; }
    }

    record Body(@NotNull String email) {
    }

    @RestController
    static class TestController {
        @PostMapping(value = "/echo", consumes = MediaType.APPLICATION_JSON_VALUE)
        String echo(@Valid @RequestBody Body body) {
            return body.email();
        }

        @GetMapping("/domain-409")
        void domain409() {
            throw new DomainException(TestError.EMAIL_TAKEN);
        }

        @GetMapping("/domain-500")
        void domain500() {
            throw new DomainException(TestError.BROKEN);
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("secret internals");
        }

        @GetMapping("/teapot")
        void teapot() {
            throw new ResponseStatusException(HttpStatus.I_AM_A_TEAPOT, "Tôi là ấm trà");
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final Logger handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attach() {
        appender.start();
        handlerLogger.addAppender(appender);
        MDC.put("traceId", "trace-123");
    }

    @AfterEach
    void detach() {
        handlerLogger.detachAppender(appender);
        MDC.clear();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void malformed_json_is_a_400_malformed_request_not_a_500_and_leaks_no_parser_detail() throws Exception {
        MvcResult result = mvc.perform(post("/echo").contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"a@b.com\", ")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        JsonNode body = json(result);
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("code").asString()).isEqualTo("MALFORMED_REQUEST");
        assertThat(body.path("message").asString()).isEqualTo("Nội dung yêu cầu không đúng định dạng");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("Exception").doesNotContain("Jackson");
        assertThat(result.getRequest().getAttribute("error.code")).isEqualTo("MALFORMED_REQUEST");
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.INFO);
        assertThat(appender.list.get(0).getThrowableProxy()).isNull();
    }

    @Test
    void error_responses_carry_the_trace_id_from_the_logging_context() throws Exception {
        MvcResult result = mvc.perform(get("/domain-409")).andReturn();

        assertThat(json(result).path("traceId").asString()).isEqualTo("trace-123");
    }

    @Test
    void without_a_trace_the_trace_id_field_is_omitted() throws Exception {
        MDC.clear();

        MvcResult result = mvc.perform(get("/domain-409")).andReturn();

        assertThat(json(result).has("traceId")).isFalse();
    }

    @Test
    void a_business_error_is_409_with_the_enum_name_as_code_and_logged_at_info_without_a_stack_trace() throws Exception {
        MvcResult result = mvc.perform(get("/domain-409")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        JsonNode body = json(result);
        assertThat(body.path("code").asString()).isEqualTo("EMAIL_TAKEN");
        assertThat(body.path("message").asString()).isEqualTo("Email này đã được dùng");
        assertThat(result.getRequest().getAttribute("error.code")).isEqualTo("EMAIL_TAKEN");
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.INFO);
        assertThat(appender.list.get(0).getThrowableProxy()).isNull();
    }

    @Test
    void a_business_error_with_a_5xx_status_is_logged_at_error_with_the_stack_trace() throws Exception {
        MvcResult result = mvc.perform(get("/domain-500")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.ERROR);
        assertThat(appender.list.get(0).getThrowableProxy()).isNotNull();
    }

    @Test
    void an_unexpected_exception_is_500_internal_error_logged_with_a_stack_trace_and_never_leaks_internals() throws Exception {
        MvcResult result = mvc.perform(get("/boom")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        assertThat(json(result).path("code").asString()).isEqualTo("INTERNAL_ERROR");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("secret internals");
        assertThat(result.getRequest().getAttribute("error.code")).isEqualTo("INTERNAL_ERROR");
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.ERROR);
        assertThat(appender.list.get(0).getThrowableProxy().getMessage()).isEqualTo("secret internals");
    }

    @Test
    void bean_validation_failures_are_400_validation_failed_with_the_field_errors() throws Exception {
        MvcResult result = mvc.perform(post("/echo").contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        JsonNode body = json(result);
        assertThat(body.path("code").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(body.path("errors").get(0).asString()).startsWith("email:");
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    void framework_errors_that_carry_their_own_status_are_client_errors_not_500() throws Exception {
        MvcResult wrongMethod = mvc.perform(get("/echo")).andReturn();
        MvcResult wrongMedia = mvc.perform(post("/echo").contentType(MediaType.TEXT_PLAIN).content("x")).andReturn();

        assertThat(wrongMethod.getResponse().getStatus()).isEqualTo(405);
        assertThat(json(wrongMethod).path("code").asString()).isEqualTo("HTTP_405");
        assertThat(wrongMedia.getResponse().getStatus()).isEqualTo(415);
        assertThat(json(wrongMedia).path("code").asString()).isEqualTo("HTTP_415");
        assertThat(appender.list).allSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.INFO));
    }

    @Test
    void response_status_exceptions_keep_their_status_and_reason_with_a_code() throws Exception {
        MvcResult result = mvc.perform(get("/teapot")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(418);
        JsonNode body = json(result);
        assertThat(body.path("code").asString()).isEqualTo("HTTP_418");
        assertThat(body.path("message").asString()).isEqualTo("Tôi là ấm trà");
    }

    @Test
    void the_handler_does_not_leave_the_error_code_in_the_logging_context() throws Exception {
        mvc.perform(get("/domain-409")).andReturn();

        assertThat(MDC.get("error.code")).isNull();
    }
}
