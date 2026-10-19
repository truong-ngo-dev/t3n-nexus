package vn.t3nexus.storefrontwebbff;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import vn.t3nexus.storefrontwebbff.infrastructure.web.SecurityErrorHandlers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KT-10 (feature 06, nhóm B, R9): 403 do CSRF và 403 do thiếu quyền có mã khác nhau để giao diện rẽ nhánh (CSRF thì lấy lại mã và thử một lần,
 * thiếu quyền thì hiện trang cấm). BFF hiện chưa có quy tắc vai nào để gây 403 thiếu quyền qua HTTP, nên kiểm trực tiếp hai bộ xử lý.
 */
class SecurityErrorHandlersTest {

    private String codeOf(org.springframework.security.web.server.authorization.ServerAccessDeniedHandler handler) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/x"));
        handler.handle(exchange, new AccessDeniedException("denied")).block();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(403);
        String body = exchange.getResponse().getBodyAsString().block();
        return body.replaceAll(".*\"code\":\"([A-Z_]+)\".*", "$1");
    }

    @Test
    void csrf_denial_and_permission_denial_have_different_stable_codes() {
        assertThat(codeOf(SecurityErrorHandlers.csrfDenied())).isEqualTo("CSRF_TOKEN_INVALID");
        assertThat(codeOf(SecurityErrorHandlers.forbidden())).isEqualTo("FORBIDDEN");
    }
}
