package vn.t3nexus.storefrontwebbff;

import org.junit.jupiter.api.Test;
import vn.t3nexus.storefrontwebbff.support.GatewayTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KT-24 (feature 06, `tech.md` T7, R19), phía BFF: địa chỉ người gọi chuyển xuống dịch vụ sau trong {@code X-Client-Ip}. Kết nối tới BFF từ
 * proxy tin cậy (cổng; ở test là loopback) thì giữ giá trị cổng đã đặt; thiếu hoặc không phải địa chỉ IP thì dùng địa chỉ kết nối.
 * Từ nơi không tin cậy: xem {@link UntrustedProxyClientIpTest}.
 */
class ClientIpTest extends GatewayTestBase {

    private String clientIpSeenByService(String headerValue) {
        IDENTITY.respond("GET", "/api/users/verify", 200, "application/json", "{}");
        var request = client().get().uri("/api/identity/users/verify?token=abc");
        if (headerValue != null) request.header("X-Client-Ip", headerValue);
        request.exchange().expectStatus().isOk();
        return IDENTITY.last().header("X-Client-Ip");
    }

    @Test
    void the_address_set_by_the_gateway_is_passed_on_when_the_connection_is_from_a_trusted_proxy() {
        assertThat(clientIpSeenByService("203.0.113.9")).isEqualTo("203.0.113.9");
    }

    @Test
    void a_missing_or_malformed_header_is_replaced_by_the_connection_address() {
        assertThat(clientIpSeenByService(null)).isEqualTo("127.0.0.1");
        assertThat(clientIpSeenByService("not-an-ip")).isEqualTo("127.0.0.1");
    }
}
