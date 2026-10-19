package vn.t3nexus.apigateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import vn.t3nexus.apigateway.infrastructure.ratelimit.ClientAddress;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

/** KT-04 (feature 06, nhóm A, R2): khóa đếm lấy từ địa chỉ kết nối, không đọc tiêu đề do client tự gửi. */
class ClientAddressTest {

    private static MockServerWebExchange exchange(String remote, String... headers) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get("/x").remoteAddress(new InetSocketAddress(remote, 4321));
        for (int i = 0; i < headers.length; i += 2) request.header(headers[i], headers[i + 1]);
        return MockServerWebExchange.from(request);
    }

    @Test
    void the_address_comes_from_the_connection_and_ignores_forwarding_headers_sent_by_the_client() {
        assertThat(ClientAddress.of(exchange("10.1.2.3", "X-Forwarded-For", "1.1.1.1, 2.2.2.2", "Forwarded", "for=3.3.3.3", "X-Real-IP", "4.4.4.4")))
                .isEqualTo("10.1.2.3");
    }

    @Test
    void two_connection_addresses_give_two_keys() {
        assertThat(ClientAddress.of(exchange("10.0.0.1"))).isNotEqualTo(ClientAddress.of(exchange("10.0.0.2")));
    }

    @Test
    void a_missing_address_gives_a_fixed_fallback_key() {
        MockServerWebExchange noAddress = MockServerWebExchange.from(MockServerHttpRequest.get("/x"));

        assertThat(ClientAddress.of(noAddress)).isEqualTo("unknown");
    }
}
