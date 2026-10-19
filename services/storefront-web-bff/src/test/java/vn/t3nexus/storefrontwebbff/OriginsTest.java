package vn.t3nexus.storefrontwebbff;

import org.junit.jupiter.api.Test;
import vn.t3nexus.storefrontwebbff.support.GatewayTestBase;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KT-28 và KT-30 (feature 06, nhóm E, ADR-0006): BFF dựng địa chỉ công khai từ tiêu đề do cổng đặt (host {@code shop} cho
 * redirect URI, host {@code auth} cho điểm cấp mã), và cookie phiên của BFF là cookie theo máy (không {@code Domain}, {@code HttpOnly},
 * tên riêng {@code STOREFRONT_SESSION} khác cookie của Xác thực nên hai nơi không ghi đè nhau).
 */
class OriginsTest extends GatewayTestBase {

    @Test
    void login_start_builds_the_redirect_uri_at_the_shop_host_and_sends_the_browser_to_the_auth_host() {
        var result = client().get().uri("/oauth2/authorization/storefront-web")
                .header("X-Forwarded-Host", "shop.localhost:8000")
                .header("X-Forwarded-Proto", "http")
                .header("X-Forwarded-Port", "8000")
                .exchange()
                .expectStatus().is3xxRedirection()
                .returnResult(Void.class);

        String location = result.getResponseHeaders().getFirst("Location");
        assertThat(location).startsWith("http://auth.localhost:8000/oauth2/authorize?");
        assertThat(location).contains("client_id=storefront-web")
                .contains("redirect_uri=http://shop.localhost:8000/login/oauth2/code/storefront-web")
                .contains("code_challenge_method=S256")
                .contains("state=");
    }

    @Test
    void the_session_cookie_is_host_only_http_only_and_named_for_the_storefront() {
        var result = client().get().uri("/oauth2/authorization/storefront-web")
                .header("X-Forwarded-Host", "shop.localhost:8000")
                .header("X-Forwarded-Proto", "http")
                .exchange().returnResult(Void.class);

        List<String> cookies = result.getResponseHeaders().get("Set-Cookie");
        assertThat(cookies).isNotNull();
        String session = cookies.stream().filter(c -> c.startsWith("STOREFRONT_SESSION=")).findFirst().orElseThrow();
        assertThat(session).containsIgnoringCase("HttpOnly").doesNotContainIgnoringCase("Domain=");
        assertThat(cookies).as("không còn cookie tên cũ").noneMatch(c -> c.startsWith("WEBGW_SESSION=") || c.startsWith("OAUTH2_SESSION="));
    }
}
