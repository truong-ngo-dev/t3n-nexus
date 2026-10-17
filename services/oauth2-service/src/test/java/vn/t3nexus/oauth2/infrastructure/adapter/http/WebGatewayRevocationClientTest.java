package vn.t3nexus.oauth2.infrastructure.adapter.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Hợp đồng báo web-gateway xóa mã khi phiên ứng dụng kết thúc (KT-07, {@code feature 03 tech.md} T7): thân chỉ có {@code sid}, xác thực
 * bằng token của service account vừa mint, và lỗi ở bất kỳ bước nào không ném ra ngoài. Chạy với một máy chủ HTTP cục bộ đóng vai
 * cả điểm cuối token lẫn web-gateway.
 */
class WebGatewayRevocationClientTest {

    private static final String SID = "01938a5e-1c00-7000-8000-0000000000a1";

    private HttpServer server;
    private final List<String> revokeBodies = new ArrayList<>();
    private final List<String> revokeAuthorizations = new ArrayList<>();
    private final List<String> tokenRequestAuthorizations = new ArrayList<>();
    private int revokeStatus = 200;
    private int tokenStatus = 200;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth2/token", exchange -> {
            tokenRequestAuthorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, tokenStatus, "{\"access_token\":\"service-token\",\"token_type\":\"Bearer\",\"expires_in\":60}");
        });
        server.createContext("/webgw/internal/sessions/revoke", exchange -> {
            revokeAuthorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            revokeBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, revokeStatus, "");
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private WebGatewayRevocationClient client() {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new WebGatewayRevocationClient(base, base, "oauth2-service-internal", "internal-secret");
    }

    @Test
    void the_body_carries_only_the_client_session_id_and_the_call_is_authenticated_with_a_fresh_service_token() {
        client().revoke(SID);

        assertThat(revokeBodies).singleElement().satisfies(body -> assertThat(body.replace(" ", "")).isEqualTo("{\"sid\":\"" + SID + "\"}"));
        assertThat(revokeAuthorizations).containsExactly("Bearer service-token");
        assertThat(tokenRequestAuthorizations).singleElement().asString().startsWith("Basic ");
    }

    @Test
    void a_failing_web_gateway_is_swallowed() {
        revokeStatus = 500;

        assertThatCode(() -> client().revoke(SID)).doesNotThrowAnyException();
        assertThat(revokeBodies).hasSize(1);
    }

    @Test
    void a_failing_token_endpoint_means_no_revoke_call_and_no_exception() {
        tokenStatus = 401;

        assertThatCode(() -> client().revoke(SID)).doesNotThrowAnyException();
        assertThat(revokeBodies).isEmpty();
    }

    @Test
    void an_unreachable_web_gateway_is_swallowed() {
        int port = server.getAddress().getPort();
        server.stop(0);

        assertThatCode(() -> new WebGatewayRevocationClient("http://127.0.0.1:" + port, "http://127.0.0.1:" + port,
                "oauth2-service-internal", "internal-secret").revoke(SID)).doesNotThrowAnyException();
    }
}
