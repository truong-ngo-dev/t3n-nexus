package vn.t3nexus.apigateway.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Lớp nền cho test của {@code api-gateway}: dựng ứng dụng thật trên cổng ngẫu nhiên, Redis thật của Testcontainers, và các máy chủ
 * HTTP giả thay Xác thực, BFF Cửa hàng và máy chủ giao diện Cửa hàng. {@link WebTestClient} gọi vào cổng như trình duyệt và đặt
 * tiêu đề {@code Host} để chọn tuyến theo host (ADR-0006); không có {@code @AutoConfigureWebTestClient} vì Boot 4 để nó ở module
 * riêng không có trong kho cục bộ, nên dựng bằng {@code bindToServer}.
 *
 * <p>Lớp con thêm thuộc tính riêng bằng {@code @SpringBootTest(properties = ...)}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class GatewayTestBase {

    protected static final String AUTH_HOST = "auth.localhost:8000";
    protected static final String SHOP_HOST = "shop.localhost:8000";
    protected static final String REDIS_PORT_OVERRIDE = "test.redis.port.override";

    protected static final FakeHttpServer OAUTH2 = new FakeHttpServer();
    protected static final FakeHttpServer BFF = new FakeHttpServer();
    protected static final FakeHttpServer FRONTEND = new FakeHttpServer();

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", RedisContainerHolder::host);
        // Lớp con có thể thay cổng Redis (giả lập Redis chết) bằng thuộc tính hệ thống: @DynamicPropertySource của lớp con chạy TRƯỚC lớp cha nên không ghi đè được
        registry.add("spring.data.redis.port", () -> System.getProperty(REDIS_PORT_OVERRIDE, String.valueOf(RedisContainerHolder.port())));
        registry.add("apigateway.routes.oauth2-service.uri", OAUTH2::url);
        registry.add("apigateway.routes.storefront-web-bff.uri", BFF::url);
        registry.add("app.gateway.frontends.shop", FRONTEND::url);
    }

    @LocalServerPort
    protected int port;

    @BeforeEach
    void resetFakesAndRedis() {
        OAUTH2.reset();
        BFF.reset();
        FRONTEND.reset();
        RedisContainerHolder.flush();
    }

    protected WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    /** POST (thân rỗng) tới cổng với tiêu đề {@code Host} như trình duyệt đã gõ tên máy đó. */
    protected WebTestClient.RequestHeadersSpec<?> post(String host, String path) {
        return client().post().uri(path).header("Host", host);
    }

    /** GET tới cổng với tiêu đề {@code Host} như trình duyệt đã gõ tên máy đó. */
    protected WebTestClient.RequestHeadersSpec<?> get(String host, String path) {
        return client().get().uri(path).header("Host", host);
    }
}
