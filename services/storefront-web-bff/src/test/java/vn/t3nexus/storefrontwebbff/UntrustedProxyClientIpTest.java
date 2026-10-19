package vn.t3nexus.storefrontwebbff;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import vn.t3nexus.storefrontwebbff.support.GatewayTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KT-24 (feature 06, `tech.md` T7, R19), phía BFF, ca gọi thẳng: kết nối tới từ địa chỉ không nằm trong danh sách proxy tin cậy
 * ({@code observability.logging.http.trusted-proxies}) thì {@code X-Client-Ip} do người gọi gửi bị bỏ, dịch vụ sau nhận địa chỉ kết nối.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "observability.logging.http.trusted-proxies=10.9.9.9"
})
class UntrustedProxyClientIpTest extends GatewayTestBase {

    @Test
    void a_forged_header_from_an_untrusted_connection_is_replaced_by_the_connection_address() {
        IDENTITY.respond("GET", "/api/users/verify", 200, "application/json", "{}");

        client().get().uri("/api/identity/users/verify?token=abc").header("X-Client-Ip", "203.0.113.9").exchange().expectStatus().isOk();

        assertThat(IDENTITY.last().header("X-Client-Ip")).isEqualTo("127.0.0.1");
    }
}
