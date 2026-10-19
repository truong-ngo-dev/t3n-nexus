package vn.t3nexus.oauth2.presentation.error;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.t3nexus.lib.ratelimiter.RateLimiter;
import vn.t3nexus.oauth2.infrastructure.PostgresContainerHolder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bảng hành vi lỗi của `oauth2-service` theo người gọi (`service.md` mục 3.1, nợ kỹ thuật mục 3). Mỗi hàng là (yêu cầu, {@code Accept}) và kết quả
 * mong đợi; test chạy mọi hàng rồi báo cùng lúc các hàng sai. Quy tắc:
 * <ul>
 *   <li>Chỉ <b>điều hướng trình duyệt</b> ({@code Accept} nêu rõ {@code text/html}) mới được chuyển sang {@code /login}; {@code *}{@code /*} và thiếu
 *       {@code Accept} không phải điều hướng. Người gọi khác nhận 401.</li>
 *   <li><b>Endpoint giao thức</b> ({@code /oauth2/token}, {@code /userinfo}) luôn 401 theo RFC 6749 và 6750, không bao giờ chuyển hướng.</li>
 *   <li>CSRF sai luôn 403 (không chuyển hướng); trang lỗi cho trình duyệt không phải trang Whitelabel của Boot.</li>
 *   <li>Lỗi tham số ở endpoint cấp mã trả về {@code redirect_uri} kèm {@code error} theo Spring Authorization Server (giữ nguyên).</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.bootstrap-servers=localhost:1",
        "spring.kafka.admin.auto-create=false",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=1",
        "server.servlet.session.cookie.secure=false",
        "app.device-key.secure=false"
})
class ErrorBehaviorTest {

    private static final String HTML = "text/html";
    private static final String JSON = "application/json";
    private static final String ANY = "*/*";
    private static final String NONE = "(none)";
    private static final String[] NON_HTML = {JSON, ANY, NONE};
    private static final String[] ALL = {HTML, JSON, ANY, NONE};

    private static final String AUTHORIZE = "/oauth2/authorize?response_type=code&client_id=storefront-web&scope=openid"
            + "&redirect_uri=http%3A%2F%2Fshop.localhost%3A8000%2Flogin%2Foauth2%2Fcode%2Fstorefront-web";

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresContainerHolder.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", PostgresContainerHolder.POSTGRES::getUsername);
        registry.add("spring.datasource.password", PostgresContainerHolder.POSTGRES::getPassword);
    }

    @MockitoBean RateLimiter rateLimiter;
    @LocalServerPort int port;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    /** Kết quả mong đợi của một hàng; {@code location} là tiền tố của {@code Location} ({@code null} nghĩa là không được có Location). */
    private record Expect(int status, String location) {
    }

    private final List<String> failures = new ArrayList<>();

    private void row(String method, String path, String body, String contentType, String[] accepts, Expect expect) throws Exception {
        for (String accept : accepts) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
            if (!accept.equals(NONE)) builder.header("Accept", accept);
            if (contentType != null) builder.header("Content-Type", contentType);
            builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());

            String location = response.headers().firstValue("Location").map(l -> l.replaceFirst("^http://localhost:\\d+", "")).orElse(null);
            boolean ok = response.statusCode() == expect.status()
                    && (expect.location() == null ? location == null : location != null && location.startsWith(expect.location()));
            if (!ok) {
                failures.add(String.format("%s %s [Accept: %s] -> %d location=%s; mong đợi %d location=%s", method, path, accept,
                        response.statusCode(), location, expect.status(), expect.location()));
            }
        }
    }

    @Test
    void every_row_of_the_error_behavior_table_holds() throws Exception {
        // đường dẫn không tồn tại: chỉ điều hướng trình duyệt được đưa tới đăng nhập, người gọi khác nhận 401
        row("GET", "/nonexistent-page", null, null, new String[] {HTML}, new Expect(302, "/login"));
        row("GET", "/nonexistent-page", null, null, NON_HTML, new Expect(401, null));
        row("GET", "/error", null, null, new String[] {HTML}, new Expect(302, "/login"));
        row("GET", "/error", null, null, NON_HTML, new Expect(401, null));

        // endpoint giao thức: luôn 401, không chuyển hướng
        row("GET", "/userinfo", null, null, ALL, new Expect(401, null));
        row("POST", "/oauth2/token", "grant_type=refresh_token&refresh_token=x", "application/x-www-form-urlencoded", ALL, new Expect(401, null));
        row("GET", "/api/anything", null, null, ALL, new Expect(401, null));

        // điểm cấp mã khi chưa có phiên: điều hướng trình duyệt tới đăng nhập; người gọi khác 401
        String withParams = AUTHORIZE + "&state=abc&code_challenge=" + "a".repeat(43) + "&code_challenge_method=S256";
        row("GET", withParams, null, null, new String[] {HTML}, new Expect(302, "/login"));
        row("GET", withParams, null, null, NON_HTML, new Expect(401, null));
        // thiếu state: Spring Authorization Server trả về redirect_uri kèm error (giữ nguyên)
        row("GET", AUTHORIZE, null, null, ALL, new Expect(302, "http://shop.localhost:8000/login/oauth2/code/storefront-web?error=invalid_request"));

        // CSRF sai luôn 403
        row("POST", "/login", "username=a&password=b", "application/x-www-form-urlencoded", ALL, new Expect(403, null));
        row("POST", "/logout", "", "application/x-www-form-urlencoded", ALL, new Expect(403, null));
        row("PUT", "/login", "x=1", "application/x-www-form-urlencoded", ALL, new Expect(403, null));

        // trang công khai và JSON công khai
        row("GET", "/login", null, null, ALL, new Expect(200, null));
        row("GET", "/.well-known/openid-configuration", null, null, ALL, new Expect(200, null));
        row("GET", "/oauth2/jwks", null, null, ALL, new Expect(200, null));
        row("POST", "/register", "{bad json", "application/json", ALL, new Expect(400, null));

        assertThat(failures).as("các hàng sai của bảng hành vi lỗi").isEmpty();
    }

    @Test
    void error_pages_for_browsers_are_ours_not_the_whitelabel_page() throws Exception {
        for (String[] req : new String[][] {{"POST", "/login"}, {"POST", "/register"}}) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + req[1]))
                    .header("Accept", HTML).header("Content-Type", req[1].equals("/register") ? "application/json" : "application/x-www-form-urlencoded");
            HttpResponse<String> response = http.send(builder.POST(HttpRequest.BodyPublishers.ofString(req[1].equals("/register") ? "{bad" : "x=1"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.body()).as(req[1]).doesNotContain("Whitelabel").doesNotContain("no explicit mapping");
        }
    }

    @Test
    void the_token_endpoint_challenges_with_www_authenticate_and_the_userinfo_endpoint_with_bearer() throws Exception {
        HttpResponse<String> token = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/oauth2/token"))
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("grant_type=refresh_token&refresh_token=x"))
                .build(), HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> userinfo = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/userinfo")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(token.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(h -> assertThat(h).startsWithIgnoringCase("Basic"));
        assertThat(userinfo.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(h -> assertThat(h).startsWithIgnoringCase("Bearer"));
    }
}
