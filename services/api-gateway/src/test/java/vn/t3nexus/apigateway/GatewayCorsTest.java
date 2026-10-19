package vn.t3nexus.apigateway;

import org.junit.jupiter.api.Test;
import vn.t3nexus.apigateway.support.GatewayTestBase;

/**
 * KT-35 (feature 06, nhóm E, ADR-0006): CORS đọc từ cấu hình, ở dev là mẫu {@code http://*.localhost:*} và vẫn cho cookie. Nguồn gốc
 * thuộc mẫu nhận đúng tiêu đề cho phép kèm {@code Allow-Credentials}; nguồn gốc lạ không nhận; không bao giờ trả {@code *} kèm cookie.
 */
class GatewayCorsTest extends GatewayTestBase {

    @Test
    void a_dev_subdomain_origin_is_allowed_with_credentials() {
        client().options().uri("/bff/auth/session").header("Host", SHOP_HOST)
                .header("Origin", "http://shop.localhost:4200")
                .header("Access-Control-Request-Method", "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://shop.localhost:4200")
                .expectHeader().valueEquals("Access-Control-Allow-Credentials", "true");
    }

    @Test
    void an_unknown_origin_gets_no_allow_headers() {
        client().options().uri("/bff/auth/session").header("Host", SHOP_HOST)
                .header("Origin", "http://evil.example")
                .header("Access-Control-Request-Method", "GET")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist("Access-Control-Allow-Origin")
                .expectHeader().doesNotExist("Access-Control-Allow-Credentials");
    }
}
