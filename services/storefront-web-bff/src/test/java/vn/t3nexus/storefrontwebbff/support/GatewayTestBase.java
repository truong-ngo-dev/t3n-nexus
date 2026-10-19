package vn.t3nexus.storefrontwebbff.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Lớp nền cho test của BFF ({@code storefront-web-bff}): dựng ứng dụng thật trên cổng ngẫu nhiên, Redis thật của Testcontainers (phiên,
 * ánh xạ, giới hạn người dùng), và các máy chủ HTTP giả thay Xác thực và các dịch vụ sau BFF. Tuyến tới từng dịch vụ trỏ vào một
 * máy chủ giả riêng để test kiểm được dịch vụ nào nhận yêu cầu và với đường dẫn nào sau khi viết lại. {@link WebTestClient} dựng
 * bằng {@code bindToServer} (Boot 4 để {@code @AutoConfigureWebTestClient} ở module riêng không có trong kho cục bộ).
 *
 * <p>Lớp con thêm thuộc tính riêng bằng {@code @SpringBootTest(properties = ...)}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class GatewayTestBase {

    protected static final FakeHttpServer OAUTH2 = new FakeHttpServer();
    protected static final FakeHttpServer IDENTITY = new FakeHttpServer();
    protected static final FakeHttpServer CUSTOMER = new FakeHttpServer();
    protected static final FakeHttpServer CATALOG = new FakeHttpServer();
    protected static final FakeHttpServer ORDER = new FakeHttpServer();
    protected static final FakeIdentityProvider IDP = new FakeIdentityProvider();

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", RedisContainerHolder::host);
        registry.add("spring.data.redis.port", RedisContainerHolder::port);
        registry.add("storefront.routes.oauth2-service.uri", OAUTH2::url);
        registry.add("storefront.routes.identity-service.uri", IDENTITY::url);
        registry.add("storefront.routes.customer-service.uri", CUSTOMER::url);
        registry.add("storefront.routes.catalog-service.uri", CATALOG::url);
        registry.add("storefront.routes.order-service.uri", ORDER::url);
        // Kênh máy chủ tới Xác thực (đổi mã, làm mới, khóa công khai): trỏ vào máy chủ giả để test tự dựng phản hồi
        registry.add("spring.security.oauth2.client.provider.storefront-web.token-uri", () -> OAUTH2.url() + "/oauth2/token");
        registry.add("spring.security.oauth2.client.provider.storefront-web.jwk-set-uri", () -> OAUTH2.url() + "/oauth2/jwks");
        registry.add("spring.security.oauth2.client.provider.storefront-web.user-info-uri", () -> OAUTH2.url() + "/userinfo");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> OAUTH2.url() + "/oauth2/jwks");
    }

    @LocalServerPort
    protected int port;

    @BeforeEach
    void resetFakesAndRedis() {
        OAUTH2.reset();
        IDENTITY.reset();
        CUSTOMER.reset();
        CATALOG.reset();
        ORDER.reset();
        RedisContainerHolder.flush();
        IDP.publishKeys(OAUTH2);
    }

    protected WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    /** Kết quả một lần đăng nhập qua BFF: cookie phiên BFF dùng cho các yêu cầu sau và địa chỉ chuyển hướng sau callback. */
    protected record LoginResult(String sessionCookie, String location, int status, String xsrfCookie, String xsrfAtStart) {

        /** Tiêu đề Cookie như trình duyệt gửi sau đăng nhập. */
        public String cookieHeader() {
            return "STOREFRONT_SESSION=" + sessionCookie + (xsrfCookie == null ? "" : "; XSRF-TOKEN=" + xsrfCookie);
        }
    }

    /**
     * Đi hết luồng mã ủy quyền với Xác thực giả: mở {@code /oauth2/authorization/storefront-web}, lấy {@code state} và {@code nonce}
     * từ địa chỉ chuyển hướng, dựng phản hồi của điểm đổi mã (token truy cập {@code sub, sid, role}, ID token cùng {@code nonce} và
     * {@code sid}), rồi gọi callback kèm cookie phiên. {@code sid} rỗng nghĩa là token không mang claim {@code sid}.
     * Gọi lại với cùng {@code sid} mô phỏng xác thực im lặng (phiên Xác thực còn, phiên BFF mới).
     */
    protected LoginResult login(String sub, String sid, String role) {
        return login(sub, sid, role, 300);
    }

    /** Như trên nhưng token truy cập có {@code expires_in} cho trước (1 giây: coi như đã hết hạn ngay, độ lệch đồng hồ 5 giây). */
    protected LoginResult login(String sub, String sid, String role, int accessExpiresIn) {
        var start = client().get().uri("/oauth2/authorization/storefront-web")
                .header("X-Forwarded-Host", "shop.localhost:8000").header("X-Forwarded-Proto", "http").header("X-Forwarded-Port", "8000")
                .exchange().returnResult(Void.class);
        String location = start.getResponseHeaders().getFirst("Location");
        var params = UriComponentsBuilder.fromUriString(location).build().getQueryParams();
        String state = UriUtils.decode(params.getFirst("state"), StandardCharsets.UTF_8);
        String nonce = UriUtils.decode(params.getFirst("nonce"), StandardCharsets.UTF_8);
        String cookie = cookieValue(start.getResponseHeaders().get("Set-Cookie"), "STOREFRONT_SESSION", null);
        String xsrfAtStart = cookieValue(start.getResponseHeaders().get("Set-Cookie"), "XSRF-TOKEN", null);

        Instant exp = Instant.now().plusSeconds(300);
        Map<String, Object> access = new java.util.HashMap<>(Map.of("sub", sub, "role", role));
        Map<String, Object> id = new java.util.HashMap<>(Map.of("sub", sub, "iss", "http://auth.localhost:8000",
                "aud", List.of("storefront-web"), "nonce", nonce));
        if (sid != null) {
            access.put("sid", sid);
            id.put("sid", sid);
        }
        String body = "{\"access_token\":\"" + IDP.jwt(access, exp) + "\",\"token_type\":\"Bearer\",\"expires_in\":" + accessExpiresIn + ","
                + "\"refresh_token\":\"refresh-" + sub + "\",\"scope\":\"openid profile email\",\"id_token\":\"" + IDP.jwt(id, exp) + "\"}";
        OAUTH2.respond("POST", "/oauth2/token", 200, "application/json", body);
        OAUTH2.respond("GET", "/userinfo", 200, "application/json", "{\"sub\":\"" + sub + "\"}");

        var callback = client().get().uri("/login/oauth2/code/storefront-web?code=c&state={s}", state)
                .header("Cookie", "STOREFRONT_SESSION=" + cookie + (xsrfAtStart == null ? "" : "; XSRF-TOKEN=" + xsrfAtStart))
                .header("X-Forwarded-Host", "shop.localhost:8000").header("X-Forwarded-Proto", "http").header("X-Forwarded-Port", "8000")
                .exchange().returnResult(Void.class);
        var setCookies = callback.getResponseHeaders().get("Set-Cookie");
        return new LoginResult(cookieValue(setCookies, "STOREFRONT_SESSION", cookie),
                callback.getResponseHeaders().getFirst("Location"), callback.getStatus().value(),
                cookieValue(setCookies, "XSRF-TOKEN", xsrfAtStart), xsrfAtStart);
    }

    /** Giá trị của cookie tên {@code name} trong các tiêu đề Set-Cookie, hoặc {@code fallback}. */
    protected static String cookieValue(List<String> setCookies, String name, String fallback) {
        if (setCookies != null) {
            for (String c : setCookies) {
                if (c.startsWith(name + "=")) return c.substring(name.length() + 1, c.indexOf(';'));
            }
        }
        return fallback;
    }

    /** Phản hồi của điểm đổi mã khi Xác thực giả nhận yêu cầu làm mới: token truy cập mới (sống 300 giây) kèm mã làm mới mới. */
    protected void respondToRefreshWithNewTokens(String sub, String sid, String role, String newAccessMarker) {
        Instant exp = Instant.now().plusSeconds(300);
        Map<String, Object> access = new java.util.HashMap<>(Map.of("sub", sub, "role", role, "marker", newAccessMarker));
        if (sid != null) access.put("sid", sid);
        OAUTH2.respond("POST", "/oauth2/token", 200, "application/json",
                "{\"access_token\":\"" + IDP.jwt(access, exp) + "\",\"token_type\":\"Bearer\",\"expires_in\":300,"
                        + "\"refresh_token\":\"refresh-next\",\"scope\":\"openid profile email\"}");
    }

    /** Các yêu cầu làm mới (grant_type=refresh_token) mà điểm đổi mã của Xác thực giả đã nhận. */
    protected List<FakeHttpServer.Received> refreshRequests() {
        return OAUTH2.requests().stream()
                .filter(r -> r.path().equals("/oauth2/token") && r.body().contains("grant_type=refresh_token")).toList();
    }
}
