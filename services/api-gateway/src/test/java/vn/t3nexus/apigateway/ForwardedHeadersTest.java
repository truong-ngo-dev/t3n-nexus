package vn.t3nexus.apigateway;

import org.junit.jupiter.api.Test;
import vn.t3nexus.apigateway.support.GatewayTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KT-27 (feature 06, nhóm E, R22): tiêu đề chuyển tiếp ở dịch vụ sau lấy từ yêu cầu thật; giá trị do client tự gửi bị bỏ, nên client
 * không giả được host, giao thức, tiền tố hay địa chỉ. Không còn {@code X-Forwarded-Prefix} (ADR-0006 bỏ tiền tố).
 */
class ForwardedHeadersTest extends GatewayTestBase {

    @Test
    void headers_sent_by_the_client_are_dropped_and_the_real_request_is_forwarded() {
        BFF.respond("GET", "/bff/auth/session", 200, "text/plain", "ok");

        get(SHOP_HOST, "/bff/auth/session")
                .header("X-Forwarded-Prefix", "/web")
                .header("X-Forwarded-Host", "evil.example")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Port", "443")
                .header("X-Forwarded-For", "1.2.3.4")
                .header("Forwarded", "for=1.2.3.4;host=evil.example;proto=https")
                .header("X-Client-Ip", "9.9.9.9")
                .exchange().expectStatus().isOk();

        var seen = BFF.last();
        assertThat(seen.header("X-Forwarded-Prefix")).as("không còn tiền tố").isNull();
        assertThat(seen.header("Forwarded")).isNull();
        assertThat(seen.header("X-Client-Ip")).as("địa chỉ kết nối thật (feature 06, T7), không phải 9.9.9.9 do client gửi").isEqualTo("127.0.0.1");
        assertThat(seen.header("X-Forwarded-Host")).as("host thật của yêu cầu").isEqualTo(SHOP_HOST);
        assertThat(seen.header("X-Forwarded-Proto")).as("giao thức thật của yêu cầu").isEqualTo("http");
        assertThat(String.valueOf(seen.header("X-Forwarded-For"))).as("không chứa địa chỉ do client gửi").doesNotContain("1.2.3.4");
        assertThat(String.valueOf(seen.header("X-Forwarded-Port"))).isNotEqualTo("443");
    }

    @Test
    void each_host_gets_its_own_forwarded_host() {
        OAUTH2.respond("GET", "/login", 200, "text/plain", "ok");
        BFF.respond("GET", "/bff/auth/session", 200, "text/plain", "ok");

        get(AUTH_HOST, "/login").exchange().expectStatus().isOk();
        get(SHOP_HOST, "/bff/auth/session").exchange().expectStatus().isOk();

        assertThat(OAUTH2.last().header("X-Forwarded-Host")).isEqualTo(AUTH_HOST);
        assertThat(BFF.last().header("X-Forwarded-Host")).isEqualTo(SHOP_HOST);
    }
}
