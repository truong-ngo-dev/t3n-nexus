package vn.t3nexus.oauth2.presentation.login;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.t3nexus.lib.ratelimiter.RateLimiter;
import vn.t3nexus.oauth2.infrastructure.PostgresContainerHolder;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Luồng đăng nhập đầy đủ của feature 02 qua HTTP thật, trên toàn bộ ngữ cảnh ứng dụng với Postgres do Testcontainers dựng
 * (Flyway chạy từ trống): đăng nhập, cấp mã, đổi mã lấy token, userinfo, làm mới, xác thực im lặng, phiên chết, sai mật khẩu.
 * Kiểm cả việc nối bean, bộ lọc, lớp bọc `OAuth2AuthorizationService`, bộ tùy biến JWT. KT-01, 02, 18, 19, 21, 22, 23, 25.
 *
 * <p>Kafka tắt hẳn (không dựng consumer, không tạo topic, địa chỉ chết) và Redis trỏ tới địa chỉ chết (giới hạn tốc độ thay bằng
 * đồ giả): test không chạm hạ tầng cục bộ của bạn.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.bootstrap-servers=localhost:1",
        "spring.kafka.admin.auto-create=false",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=1",
        "server.servlet.session.cookie.secure=false",
        "app.device-key.secure=false",
        "logging.file.name=" + LoginFlowIntegrationTest.LOG_FILE
})
class LoginFlowIntegrationTest {

    private static final String PASSWORD     = "Matkhau123";
    static final String LOG_FILE = "target/login-flow-test/oauth2-service.json";

    private static final String CLIENT_ID    = "web-gateway";
    private static final String CLIENT_SECRET = "secret";
    private static final String REDIRECT_URI = "http://localhost:8000/web/login/oauth2/code/web-gateway";

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresContainerHolder.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", PostgresContainerHolder.POSTGRES::getUsername);
        registry.add("spring.datasource.password", PostgresContainerHolder.POSTGRES::getPassword);
    }

    @MockitoBean RateLimiter rateLimiter;
    // Mock ở lời gọi HTTP sang web-gateway (không phải ở EndedSessionNotifier) để chạy thật bộ báo và việc tra client đã đăng ký
    @MockitoBean vn.t3nexus.oauth2.infrastructure.adapter.http.WebGatewayRevocationClient webGatewayRevocationClient;

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;

    private final JsonMapper json = JsonMapper.builder().build();
    private CookieManager cookies;
    private HttpClient http;
    private String email;

    private void newBrowser() {
        cookies = new CookieManager();
        http = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();
        when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(true);
    }

    /** Xóa file log của lần chạy trước, trước khi Spring khởi động ghi log. */
    @org.junit.jupiter.api.BeforeAll
    static void deleteOldLogFile() throws java.io.IOException {
        java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(LOG_FILE));
    }

    @AfterEach
    void cleanUp() {
        if (email == null) return;
        jdbc.update("delete from oauth2_authorization where principal_name in (select id::text from user_accounts where email = ?)", email);
        jdbc.update("delete from login_sessions where user_account_id in (select id from user_accounts where email = ?)", email);
        jdbc.update("delete from user_accounts where email = ?", email);
    }

    // ───────────── hỗ trợ HTTP ─────────────

    private String base() {
        return "http://localhost:" + port;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String registerAccount() throws Exception {
        email = "flow-" + UUID.randomUUID() + "@example.com";
        HttpRequest request = HttpRequest.newBuilder(URI.create(base() + "/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return json.readTree(response.body()).path("data").path("userAccountId").asString();
    }

    private static final String CODE_VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";   // mẫu của RFC 7636 phụ lục B

    private static String codeChallenge(String verifier) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    java.security.MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String authorizeUrl(String state) {
        return authorizeUrlWithoutPkce(state) + "&code_challenge=" + codeChallenge(CODE_VERIFIER) + "&code_challenge_method=S256";
    }

    private String authorizeUrlWithoutPkce(String state) {
        return base() + "/oauth2/authorize?response_type=code&client_id=" + CLIENT_ID + "&redirect_uri=" + enc(REDIRECT_URI)
                + "&scope=" + enc("openid profile email") + (state == null ? "" : "&state=" + state);
    }

    private HttpResponse<String> get(String url, String... headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).header("Accept", "text/html");
        for (int i = 0; i < headers.length; i += 2) builder.setHeader(headers[i], headers[i + 1]);
        return http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Mở trang đăng nhập như trình duyệt (cấp DEVICE_KEY, mã CSRF) rồi gửi form kèm mã CSRF lấy từ trang. */
    private HttpResponse<String> postLogin(String username, String password) throws Exception {
        HttpResponse<String> page = get(base() + "/login");
        Matcher csrf = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.body());
        assertThat(csrf.find()).as("the login form carries a CSRF token").isTrue();
        return postLoginWithCsrf(username, password, csrf.group(1));
    }

    private HttpResponse<String> postLoginWithCsrf(String username, String password, String csrfToken) throws Exception {
        String body = "username=" + enc(username) + "&password=" + enc(password) + (csrfToken == null ? "" : "&_csrf=" + enc(csrfToken));
        HttpRequest request = HttpRequest.newBuilder(URI.create(base() + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded").header("Accept", "text/html")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode token(String form) throws Exception {
        HttpResponse<String> response = tokenResponse(form);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return json.readTree(response.body());
    }

    private HttpResponse<String> tokenResponse(String form) throws Exception {
        String basic = Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(base() + "/oauth2/token"))
                .header("Authorization", "Basic " + basic).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String location(HttpResponse<?> response) {
        assertThat(response.statusCode()).as("expected a redirect").isIn(301, 302, 303, 307);
        return response.headers().firstValue("Location").orElseThrow();
    }

    private static Map<String, String> query(String url) {
        String q = URI.create(url).getRawQuery();
        return q == null ? Map.of() : java.util.Arrays.stream(q.split("&")).map(p -> p.split("=", 2))
                .collect(Collectors.toMap(p -> p[0], p -> p.length > 1
                        ? java.net.URLDecoder.decode(p[1], StandardCharsets.UTF_8) : ""));
    }

    private JsonNode claims(String jwt) throws Exception {
        return json.readTree(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
    }

    /** Đăng nhập rồi tới được mã ủy quyền; trả về mã. */
    private String loginAndGetCode() throws Exception {
        String state = "st-" + UUID.randomUUID();
        assertThat(location(get(authorizeUrl(state)))).contains("/login");
        String afterLogin = location(postLogin(email, PASSWORD));
        assertThat(afterLogin).contains("/oauth2/authorize");
        return codeFrom(get(afterLogin), null);
    }

    private String codeFrom(HttpResponse<String> authorizeResponse, String expectedState) {
        String redirect = location(authorizeResponse);
        assertThat(redirect).startsWith(REDIRECT_URI);
        Map<String, String> q = query(redirect);
        if (expectedState != null) assertThat(q.get("state")).isEqualTo(expectedState);
        assertThat(q).containsKey("code");
        return q.get("code");
    }

    private JsonNode exchange(String code) throws Exception {
        return token("grant_type=authorization_code&code=" + enc(code) + "&redirect_uri=" + enc(REDIRECT_URI)
                + "&code_verifier=" + CODE_VERIFIER);
    }

    // ───────────── các luồng ─────────────

    @Test
    void a_full_login_opens_one_user_session_and_issues_tokens_bound_to_one_client_session() throws Exception {
        newBrowser();
        String accountId = registerAccount();
        HttpCookie device = new HttpCookie("DEVICE_KEY", "raw-device-cookie-" + UUID.randomUUID());
        device.setPath("/");
        cookies.getCookieStore().add(URI.create(base()), device);

        JsonNode tokens = exchange(loginAndGetCode());

        // một phiên người dùng, mã thiết bị chỉ ở dạng băm
        Map<String, Object> session = jdbc.queryForMap(
                "select id, idp_session_id, device_key, expires_at > issued_at as ok from login_sessions where user_account_id = ?::uuid", accountId);
        assertThat(session.get("device_key").toString()).hasSize(64).doesNotContain(device.getValue());
        // access token: hợp đồng danh tính, cắt hạn, không có claim cũ
        JsonNode access = claims(tokens.path("access_token").asString());
        assertThat(access.path("sub").asString()).isEqualTo(accountId);
        assertThat(access.path("role").asString()).isEqualTo("USER");
        assertThat(UUID.fromString(access.path("sid").asString()).version()).isEqualTo(7);
        assertThat(access.path("exp").asLong() - access.path("iat").asLong()).isLessThanOrEqualTo(901);   // iat cắt về giây nên lệch tối đa 1s
        assertThat(access.propertyNames()).doesNotContain("oss_id", "roles", "email");
        assertThat(claims(tokens.path("id_token").asString()).path("sid").asString()).isEqualTo(access.path("sid").asString());
        // phiên ứng dụng khớp sid và trỏ tới đúng bản ghi ủy quyền; mã cookie Spring không rò vào token hay bản ghi ủy quyền
        Map<String, Object> clientSession = jdbc.queryForMap(
                "select id::text as id, authorization_id from client_sessions where login_session_id = ?", session.get("id"));
        assertThat(clientSession.get("id")).isEqualTo(access.path("sid").asString());
        assertThat(jdbc.queryForObject("select count(*) from oauth2_authorization where id = ?", Integer.class,
                clientSession.get("authorization_id"))).isEqualTo(1);
        // KT-25: mã cookie của Spring không có trong token, bản ghi ủy quyền (event và log: xem `LoginSessionTest`, `RecordClientSessionIssuedTest` và test log bên dưới)
        String idp = session.get("idp_session_id").toString();
        assertThat(tokens.toString()).doesNotContain(idp);
        assertThat(jdbc.queryForObject("select count(*) from oauth2_authorization where attributes like ?", Integer.class,
                "%" + idp + "%")).isZero();
    }

    @Test
    void userinfo_works_through_the_lookup_by_the_sid_inside_the_access_token() throws Exception {
        newBrowser();
        String accountId = registerAccount();
        JsonNode tokens = exchange(loginAndGetCode());

        HttpResponse<String> userinfo = http.send(HttpRequest.newBuilder(URI.create(base() + "/userinfo"))
                .header("Authorization", "Bearer " + tokens.path("access_token").asString()).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(userinfo.statusCode()).as(userinfo.body()).isEqualTo(200);
        assertThat(json.readTree(userinfo.body()).path("sub").asString()).isEqualTo(accountId);
    }

    @Test
    void the_api_chain_accepts_the_access_token_it_issued_and_rejects_everything_else() throws Exception {
        newBrowser();
        registerAccount();
        JsonNode tokens = exchange(loginAndGetCode());
        String access = tokens.path("access_token").asString();
        String status = base() + "/api/v1/me/password/status";

        HttpResponse<String> valid = http.send(HttpRequest.newBuilder(URI.create(status))
                .header("Authorization", "Bearer " + access).GET().build(), HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> none = http.send(HttpRequest.newBuilder(URI.create(status)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> garbage = http.send(HttpRequest.newBuilder(URI.create(status))
                .header("Authorization", "Bearer not-a-jwt").GET().build(), HttpResponse.BodyHandlers.ofString());
        // chữ ký sai: giữ phần đầu và thân, thay chữ ký
        String[] parts = access.split("\\.");
        HttpResponse<String> tampered = http.send(HttpRequest.newBuilder(URI.create(status))
                .header("Authorization", "Bearer " + parts[0] + "." + parts[1] + "." + "A".repeat(parts[2].length()))
                .GET().build(), HttpResponse.BodyHandlers.ofString());

        assertThat(valid.statusCode()).as(valid.body()).isEqualTo(200);
        assertThat(none.statusCode()).isEqualTo(401);
        assertThat(garbage.statusCode()).isEqualTo(401);
        assertThat(tampered.statusCode()).isEqualTo(401);
    }

    @Test
    void the_completion_log_line_carries_the_user_id_after_the_token_is_parsed_and_never_a_cookie_or_token() throws Exception {
        // KT-08 (user.id) và KT-25 (không có cookie hay token trong log)
        newBrowser();
        String accountId = registerAccount();
        JsonNode tokens = exchange(loginAndGetCode());
        String accessToken = tokens.path("access_token").asString();

        http.send(HttpRequest.newBuilder(URI.create(base() + "/userinfo")).header("Authorization", "Bearer " + accessToken).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        JsonNode line = awaitLogLine(l -> "http.request".equals(l.path("event").path("dataset").asString())
                && "/userinfo".equals(l.path("url").path("path").asString())
                && accountId.equals(l.path("user").path("id").asString()));
        assertThat(line.path("http").path("response").path("status_code").asInt()).isEqualTo(200);
        String log = java.nio.file.Files.readString(java.nio.file.Path.of(LOG_FILE));
        assertThat(log).doesNotContain(accessToken).doesNotContain(tokens.path("refresh_token").asString());
        // đăng nhập có đi qua cookie phiên: giá trị cookie không bao giờ vào log
        cookies.getCookieStore().getCookies().forEach(c -> assertThat(log).doesNotContain(c.getValue()));
    }

    /** Log được ghi bất đồng bộ: chờ có giới hạn dòng JSON thỏa điều kiện. */
    private JsonNode awaitLogLine(java.util.function.Predicate<JsonNode> condition) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
        while (true) {
            for (String line : java.nio.file.Files.readString(java.nio.file.Path.of(LOG_FILE)).split("\\R")) {
                if (!line.startsWith("{")) continue;
                try {
                    JsonNode node = json.readTree(line);
                    if (condition.test(node)) return node;
                } catch (RuntimeException notJson) {
                    // dòng cắt dở bị bỏ qua
                }
            }
            if (System.nanoTime() > deadline) throw new AssertionError("không có dòng log mong đợi");
            Thread.sleep(20);
        }
    }

    @Test
    void a_refresh_with_the_sid_keeps_the_sid_and_a_refresh_without_it_is_rejected() throws Exception {
        newBrowser();
        registerAccount();
        JsonNode tokens = exchange(loginAndGetCode());
        String sid = claims(tokens.path("access_token").asString()).path("sid").asString();
        String refresh = tokens.path("refresh_token").asString();

        HttpResponse<String> withoutSid = tokenResponse("grant_type=refresh_token&refresh_token=" + enc(refresh));
        JsonNode refreshed = token("grant_type=refresh_token&refresh_token=" + enc(refresh) + "&sid=" + enc(sid));

        assertThat(withoutSid.statusCode()).isEqualTo(400);
        assertThat(withoutSid.body()).contains("invalid_grant");
        assertThat(claims(refreshed.path("access_token").asString()).path("sid").asString()).isEqualTo(sid);
        assertThat(refreshed.path("refresh_token").asString()).isNotEqualTo(refresh);          // xoay vòng
        assertThat(jdbc.queryForObject("select count(*) from client_sessions where id = ?::uuid", Integer.class, sid)).isEqualTo(1);
        // refresh token cũ không dùng lại được
        assertThat(tokenResponse("grant_type=refresh_token&refresh_token=" + enc(refresh) + "&sid=" + enc(sid)).statusCode())
                .isEqualTo(400);
    }

    @Test
    void a_refresh_with_the_sid_of_another_session_is_rejected() throws Exception {
        newBrowser();
        registerAccount();
        JsonNode tokens = exchange(loginAndGetCode());

        HttpResponse<String> response = tokenResponse("grant_type=refresh_token&refresh_token="
                + enc(tokens.path("refresh_token").asString()) + "&sid=" + enc(UUID.randomUUID().toString()));

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void a_refresh_after_the_session_has_expired_is_invalid_grant_and_issues_nothing() throws Exception {
        // KT-20
        newBrowser();
        String accountId = registerAccount();
        JsonNode tokens = exchange(loginAndGetCode());
        String sid = claims(tokens.path("access_token").asString()).path("sid").asString();
        String refresh = tokens.path("refresh_token").asString();
        // hạn phiên đã trôi qua, ở cả hai nơi giữ hạn: hàng phiên của ta và bản chụp hạn trong bản ghi ủy quyền
        jdbc.update("update login_sessions set issued_at = now() - interval '2 days', expires_at = now() - interval '1 day' where user_account_id = ?::uuid", accountId);
        jdbc.update("update oauth2_authorization set attributes = regexp_replace(attributes, '\"session_expires_at\":\"[^\"]*\"', "
                + "'\"session_expires_at\":\"2020-01-01T00:00:00Z\"') where principal_name = ?", accountId);
        assertThat(jdbc.queryForObject("select count(*) from oauth2_authorization where principal_name = ? and attributes like '%2020-01-01T00:00:00Z%'",
                Integer.class, accountId)).isEqualTo(1);

        HttpResponse<String> response = tokenResponse("grant_type=refresh_token&refresh_token=" + enc(refresh) + "&sid=" + enc(sid));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("invalid_grant").doesNotContain("access_token");
    }

    @Test
    void silent_authentication_reuses_the_login_session_and_the_sid_and_removes_the_old_authorization() throws Exception {
        newBrowser();
        String accountId = registerAccount();
        JsonNode first = exchange(loginAndGetCode());
        String sid = claims(first.path("access_token").asString()).path("sid").asString();

        // lần xin mã thứ hai với cùng cookie: không hỏi lại mật khẩu
        String secondCode = codeFrom(get(authorizeUrl("again")), "again");
        JsonNode second = exchange(secondCode);

        assertThat(claims(second.path("access_token").asString()).path("sid").asString()).isEqualTo(sid);
        assertThat(jdbc.queryForObject("select count(*) from login_sessions where user_account_id = ?::uuid", Integer.class, accountId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from client_sessions where id = ?::uuid", Integer.class, sid)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from oauth2_authorization where principal_name = ?", Integer.class, accountId))
                .isEqualTo(1);                                                       // bản ghi ủy quyền cũ đã bị xóa
    }

    @Test
    void a_session_that_was_deleted_sends_the_user_back_to_the_login_page_instead_of_issuing_a_code() throws Exception {
        newBrowser();
        String accountId = registerAccount();
        exchange(loginAndGetCode());
        jdbc.update("delete from login_sessions where user_account_id = ?::uuid", accountId);     // cookie Spring còn sống

        String redirect = location(get(authorizeUrl("dead")));

        assertThat(redirect).contains("/login").doesNotContain("code=");
    }

    @Test
    void a_wrong_password_opens_no_session_and_returns_to_the_login_page() throws Exception {
        newBrowser();
        registerAccount();
        get(authorizeUrl("x"));

        String redirect = location(postLogin(email, "Sai-mat-khau-1"));

        assertThat(redirect).endsWith("/login?error");
        assertThat(jdbc.queryForObject("select count(*) from login_sessions", Integer.class)).isZero();
        assertThat(location(postLogin("khong-co-" + UUID.randomUUID() + "@example.com", PASSWORD))).endsWith("/login?error");
    }

    /**
     * Chạy chính tệp {@code infra/seed/admin.sql} (feature 04) bằng {@code psql} trong container Postgres của test, trên cơ sở dữ liệu
     * mà ứng dụng đang dùng, với email nội bộ ngẫu nhiên và mật khẩu băm tại chỗ. Trả mã tài khoản vừa tạo.
     */
    private String seedAdminAccount() throws Exception {
        email = "quantri-" + UUID.randomUUID() + "@t3nexus.com.vn";
        var pg = PostgresContainerHolder.POSTGRES;
        pg.copyFileToContainer(org.testcontainers.utility.MountableFile.forHostPath(
                java.nio.file.Path.of("..", "..", "infra", "seed", "admin.sql").toAbsolutePath().normalize()), "/tmp/seed-admin-login.sql");
        String hash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(10).encode(PASSWORD);
        var result = pg.execInContainer("psql", "-U", pg.getUsername(), "-d", pg.getDatabaseName(),
                "-v", "email=" + email, "-v", "hash=" + hash, "-f", "/tmp/seed-admin-login.sql");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        return jdbc.queryForObject("select id::text from user_accounts where email = ?", String.class, email);
    }

    // KT-08 (feature 04, S4): tài khoản Quản trị do script tạo đăng nhập qua luồng thật và token mang vai ADMIN
    @Test
    void the_seeded_admin_logs_in_through_the_real_flow_and_the_token_carries_the_admin_role() throws Exception {
        newBrowser();
        String adminId = seedAdminAccount();

        JsonNode tokens = exchange(loginAndGetCode());

        JsonNode access = claims(tokens.path("access_token").asString());
        assertThat(access.path("sub").asString()).isEqualTo(adminId);
        assertThat(access.path("role").asString()).isEqualTo("ADMIN");
        assertThat(UUID.fromString(access.path("sid").asString()).version()).isEqualTo(7);
        assertThat(claims(tokens.path("id_token").asString()).path("sid").asString()).isEqualTo(access.path("sid").asString());
    }

    // KT-08 (sai mật khẩu): Quản trị bị từ chối như mọi tài khoản, không mở phiên
    @Test
    void the_seeded_admin_with_a_wrong_password_is_rejected_and_opens_no_session() throws Exception {
        newBrowser();
        String adminId = seedAdminAccount();
        get(authorizeUrl("x"));

        String redirect = location(postLogin(email, "Sai-mat-khau-1"));

        assertThat(redirect).endsWith("/login?error");
        assertThat(jdbc.queryForObject("select count(*) from login_sessions where user_account_id = ?::uuid", Integer.class, adminId)).isZero();
    }

    private String csrfFrom(HttpResponse<String> page) {
        Matcher csrf = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.body());
        assertThat(csrf.find()).isTrue();
        return csrf.group(1);
    }

    @Test
    void the_csrf_token_lives_in_its_own_long_lived_cookie_not_in_the_session() throws Exception {
        newBrowser();

        HttpResponse<String> page = get(base() + "/login");

        String cookie = page.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow();
        assertThat(cookie).contains("HttpOnly", "SameSite=Lax", "Max-Age=2592000");
    }

    @Test
    void a_form_stays_valid_when_the_session_is_lost_before_it_is_submitted() throws Exception {
        newBrowser();
        registerAccount();
        get(authorizeUrl("x"));
        String token = csrfFrom(get(base() + "/login"));
        // session hết hạn hoặc bị xóa trong lúc người dùng đang điền form: chỉ cookie CSRF còn lại
        CookieManager withoutSession = new CookieManager();
        cookies.getCookieStore().getCookies().stream().filter(c -> !c.getName().equals("OAUTH2_SESSION"))
                .forEach(c -> withoutSession.getCookieStore().add(URI.create(base()), c));
        cookies = withoutSession;
        http = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();
        assertThat(cookies.getCookieStore().getCookies()).extracting(HttpCookie::getName).doesNotContain("OAUTH2_SESSION");

        HttpResponse<String> response = postLoginWithCsrf(email, PASSWORD, token);

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(jdbc.queryForObject("select count(*) from login_sessions where user_account_id in (select id from user_accounts where email = ?)",
                Integer.class, email)).isEqualTo(1);
    }

    @Test
    void a_form_opened_before_login_is_still_valid_after_it_because_the_token_is_not_rotated() throws Exception {
        newBrowser();
        registerAccount();
        get(authorizeUrl("x"));
        String token = csrfFrom(get(base() + "/login"));
        assertThat(postLoginWithCsrf(email, PASSWORD, token).statusCode()).isEqualTo(302);

        // nhấn Back rồi gửi lại cùng form
        assertThat(postLoginWithCsrf(email, PASSWORD, token).statusCode()).isEqualTo(302);
    }

    @Test
    void a_login_post_without_a_csrf_token_is_rejected_and_opens_no_session() throws Exception {
        newBrowser();
        registerAccount();
        get(authorizeUrl("x"));

        HttpResponse<String> response = postLoginWithCsrf(email, PASSWORD, null);
        // KT-29: 403, không có phiên nào được mở
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("select count(*) from login_sessions", Integer.class)).isZero();
        assertThat(postLoginWithCsrf(email, PASSWORD, "forged-token").statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("select count(*) from login_sessions", Integer.class)).isZero();
    }

    @Test
    void a_login_over_the_attempt_limit_gets_429_with_retry_after_and_never_reaches_the_password_check() throws Exception {
        newBrowser();
        registerAccount();
        when(rateLimiter.tryAcquire(org.mockito.ArgumentMatchers.startsWith("login_pair:"), anyInt(), any())).thenReturn(false);

        HttpResponse<String> response = postLogin(email, PASSWORD);   // KT-15: mật khẩu ĐÚNG mà vẫn bị chặn

        assertThat(response.statusCode()).isEqualTo(429);
        assertThat(response.headers().firstValue("Retry-After")).contains("300");
        assertThat(response.body()).contains("quá nhiều lần");
        // không kiểm mật khẩu nên không có phiên nào được mở
        assertThat(jdbc.queryForObject("select count(*) from login_sessions where user_account_id in (select id from user_accounts where email = ?)",
                Integer.class, email)).isZero();
    }

    @Test
    void when_the_attempt_counter_fails_the_login_still_works() throws Exception {
        newBrowser();
        registerAccount();
        when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenThrow(new IllegalStateException("redis is down"));

        // KT-17: Redis lỗi thì mở cửa, đăng nhập vẫn ra mã ủy quyền
        assertThat(loginAndGetCode()).isNotBlank();
    }

    @Test
    void an_authorization_request_without_state_is_refused_with_invalid_request_on_the_registered_redirect() throws Exception {
        // KT-30 (state bắt buộc)
        newBrowser();
        registerAccount();
        // Spring kiểm tra tham số trước cả khi chuyển sang đăng nhập: lỗi quay về redirect_uri đã đăng ký
        String redirect = location(get(authorizeUrl(null)));

        assertThat(redirect).startsWith(REDIRECT_URI);
        assertThat(query(redirect)).containsEntry("error", "invalid_request").doesNotContainKey("code");
        assertThat(jdbc.queryForObject("select count(*) from oauth2_authorization where principal_name in (select id::text from user_accounts where email = ?)",
                Integer.class, email)).isZero();
    }

    @Test
    void the_browser_client_must_use_pkce_for_both_the_request_and_the_exchange() throws Exception {
        // KT-30 (PKCE bắt buộc cho cả yêu cầu cấp mã và đổi mã)
        newBrowser();
        registerAccount();

        // thiếu code_challenge: bị từ chối ở yêu cầu cấp mã
        String redirect = location(get(authorizeUrlWithoutPkce("no-pkce")));
        assertThat(redirect).startsWith(REDIRECT_URI);
        assertThat(query(redirect)).containsEntry("error", "invalid_request").doesNotContainKey("code");

        // có code_challenge nhưng đổi mã bằng verifier sai: bị từ chối ở /oauth2/token
        String code = loginAndGetCode();
        HttpResponse<String> wrongVerifier = tokenResponse("grant_type=authorization_code&code=" + enc(code)
                + "&redirect_uri=" + enc(REDIRECT_URI) + "&code_verifier=" + "x".repeat(50));
        assertThat(wrongVerifier.statusCode()).isEqualTo(400);
        // thiếu hẳn verifier
        String code2 = codeFrom(get(authorizeUrl("again")), "again");   // đã đăng nhập: cấp mã ngay
        assertThat(tokenResponse("grant_type=authorization_code&code=" + enc(code2) + "&redirect_uri=" + enc(REDIRECT_URI)).statusCode())
                .isEqualTo(400);
    }

    /** Yêu cầu cấp mã với client và redirect URI tùy ý (có PKCE và state, đủ để chỉ còn lại lỗi về client hoặc redirect). */
    private String authorizeUrlFor(String clientId, String redirectUri) {
        return base() + "/oauth2/authorize?response_type=code&client_id=" + enc(clientId) + "&redirect_uri=" + enc(redirectUri)
                + "&scope=" + enc("openid profile email") + "&state=s1"
                + "&code_challenge=" + codeChallenge(CODE_VERIFIER) + "&code_challenge_method=S256";
    }

    private void assertNoCodeWasIssuedTo(HttpResponse<String> response, String untrustedRedirect) {
        // Spring không chuyển hướng tới một redirect_uri chưa tin được: trả lỗi tại chỗ, không có mã ở bất kỳ đâu
        String location = response.headers().firstValue("Location").orElse("");
        assertThat(location).doesNotStartWith(untrustedRedirect).doesNotContain("code=");
        assertThat(response.body()).doesNotContain("code=");
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void the_spring_session_of_a_logged_in_user_lives_as_long_as_the_login_session_and_an_anonymous_one_keeps_thirty_minutes() throws Exception {
        newBrowser();
        String accountId = registerAccount();

        // chưa đăng nhập: yêu cầu authorize tạo một session vô danh với hạn mặc định 30 phút (1800 giây)
        get(authorizeUrl("anon"));
        assertThat(jdbc.queryForObject("select max_inactive_interval from spring_session order by creation_time desc limit 1", Integer.class))
                .isEqualTo(1800);

        // đã đăng nhập: session gắn với phiên của ta sống tới hạn phiên (người dùng: 30 ngày), không còn 30 phút
        loginAndGetCode();
        Integer idle = jdbc.queryForObject("select s.max_inactive_interval from spring_session s join login_sessions l on l.idp_session_id = s.session_id "
                + "where l.user_account_id = ?::uuid", Integer.class, accountId);
        Long lifetimeSeconds = jdbc.queryForObject("select extract(epoch from (expires_at - issued_at))::bigint from login_sessions "
                + "where user_account_id = ?::uuid", Long.class, accountId);
        assertThat(idle).isNotNull().isGreaterThan(1800);
        assertThat((long) idle).isBetween(lifetimeSeconds - 60, lifetimeSeconds);   // còn lại tính từ lúc mở phiên, lệch vài giây
    }

    // ───────────── Đăng xuất (feature 03): KT-01 đến KT-21 ─────────────

    private static final String POST_LOGOUT_URI = "http://localhost:4200";   // đã đăng ký cho web-gateway ở V4

    private HttpResponse<String> logout(String hint, String postLogoutRedirectUri, String state) throws Exception {
        StringBuilder url = new StringBuilder(base() + "/connect/logout?state=" + state);
        if (hint != null) url.append("&id_token_hint=").append(enc(hint));
        if (postLogoutRedirectUri != null) url.append("&post_logout_redirect_uri=").append(enc(postLogoutRedirectUri));
        return get(url.toString());
    }

    private int rows(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private int loginSessions(String accountId) {
        return rows("select count(*) from login_sessions where user_account_id = ?::uuid", accountId);
    }

    private int authorizations(String accountId) {
        return rows("select count(*) from oauth2_authorization where principal_name = ?", accountId);
    }

    /** Đổi một ký tự ở đầu phần chữ ký (không phải ký tự cuối: các bit đệm của base64url có thể không làm đổi byte nào). */
    private static String tampered(String jwt) {
        int signatureStart = jwt.lastIndexOf('.') + 1;
        char original = jwt.charAt(signatureStart);
        return jwt.substring(0, signatureStart) + (original == 'A' ? 'B' : 'A') + jwt.substring(signatureStart + 1);
    }

    @Test
    void logout_ends_the_session_removes_every_authorization_and_leaves_nothing_usable() throws Exception {
        // KT-01, 02, 05, 07, 11, 21
        newBrowser();
        String accountId = registerAccount();
        HttpCookie device = new HttpCookie("DEVICE_KEY", "raw-device-cookie-" + UUID.randomUUID());
        device.setPath("/");
        cookies.getCookieStore().add(URI.create(base()), device);
        JsonNode tokens = exchange(loginAndGetCode());
        String sid = claims(tokens.path("access_token").asString()).path("sid").asString();
        JsonNode idToken = claims(tokens.path("id_token").asString());
        assertThat(idToken.path("sid").asString()).isEqualTo(sid);                  // KT-21: sid là mã phiên ứng dụng
        assertThat(idToken.propertyNames()).doesNotContain("auth_time");            // KT-21: Spring không sinh nữa (sổ phiên rỗng)
        assertThat(rows("select count(*) from spring_session where principal_name = ?", accountId)).isEqualTo(1);

        HttpResponse<String> response = logout(tokens.path("id_token").asString(), POST_LOGOUT_URI, "bye");

        assertThat(location(response)).startsWith(POST_LOGOUT_URI).contains("state=bye");
        assertThat(loginSessions(accountId)).isZero();
        assertThat(rows("select count(*) from client_sessions where id = ?::uuid", sid)).isZero();
        assertThat(authorizations(accountId)).isZero();
        assertThat(rows("select count(*) from spring_session where principal_name = ?", accountId)).isZero();   // KT-11
        assertThat(cookies.getCookieStore().get(URI.create(base()))).extracting(HttpCookie::getName).contains("DEVICE_KEY");
        org.mockito.Mockito.verify(webGatewayRevocationClient).revoke(sid);          // KT-07: bộ báo thật tra ra client web-gateway

        // KT-05: không còn dùng được gì
        assertThat(tokenResponse("grant_type=refresh_token&refresh_token=" + enc(tokens.path("refresh_token").asString())
                + "&sid=" + enc(sid)).body()).contains("invalid_grant");
        assertThat(location(get(authorizeUrl("after")))).contains("/login");
        HttpResponse<String> userinfo = http.send(HttpRequest.newBuilder(URI.create(base() + "/userinfo"))
                .header("Authorization", "Bearer " + tokens.path("access_token").asString()).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(userinfo.statusCode()).isEqualTo(401);
    }

    @Test
    void a_second_logout_with_the_same_hint_goes_home_without_an_error() throws Exception {
        // KT-14
        newBrowser();
        String accountId = registerAccount();
        JsonNode tokens = exchange(loginAndGetCode());
        String hint = tokens.path("id_token").asString();
        logout(hint, POST_LOGOUT_URI, "one");

        HttpResponse<String> again = logout(hint, POST_LOGOUT_URI, "two");

        assertThat(again.statusCode()).isEqualTo(302);
        assertThat(location(again)).isEqualTo(base() + "/");
        assertThat(loginSessions(accountId)).isZero();
    }

    @Test
    void a_logout_without_a_login_cookie_ends_nothing_and_still_redirects() throws Exception {
        // KT-03, 23
        newBrowser();
        String accountId = registerAccount();
        JsonNode tokens = exchange(loginAndGetCode());
        JsonNode second = exchange(codeFrom(get(authorizeUrl("again")), "again"));   // hint mới nhất
        org.mockito.Mockito.clearInvocations(webGatewayRevocationClient);

        newBrowser();   // trình duyệt khác, không cookie
        HttpResponse<String> response = logout(second.path("id_token").asString(), POST_LOGOUT_URI, "nocookie");

        assertThat(location(response)).startsWith(POST_LOGOUT_URI);
        assertThat(loginSessions(accountId)).isEqualTo(1);
        assertThat(authorizations(accountId)).isEqualTo(1);
        org.mockito.Mockito.verifyNoInteractions(webGatewayRevocationClient);
        assertThat(tokens.path("refresh_token").asString()).isNotEmpty();
    }

    @Test
    void an_unregistered_landing_page_or_a_missing_hint_is_refused_and_the_session_survives() throws Exception {
        // KT-09, 10
        newBrowser();
        String accountId = registerAccount();
        JsonNode tokens = exchange(loginAndGetCode());
        String hint = tokens.path("id_token").asString();

        assertThat(logout(hint, "http://evil.example/after", "x").statusCode()).isEqualTo(400);
        assertThat(logout(null, POST_LOGOUT_URI, "x").statusCode()).isEqualTo(400);

        assertThat(loginSessions(accountId)).isEqualTo(1);
        assertThat(authorizations(accountId)).isEqualTo(1);
    }

    @Test
    void a_forged_or_garbage_hint_goes_home_and_does_not_touch_the_session() throws Exception {
        // KT-10, 18
        newBrowser();
        String accountId = registerAccount();
        JsonNode tokens = exchange(loginAndGetCode());

        for (String bad : new String[]{tampered(tokens.path("id_token").asString()), "garbage", "a.b.c"}) {
            HttpResponse<String> response = logout(bad, POST_LOGOUT_URI, "x");
            assertThat(response.statusCode()).isEqualTo(302);
            assertThat(location(response)).isEqualTo(base() + "/");
        }

        assertThat(loginSessions(accountId)).isEqualTo(1);
        assertThat(rows("select count(*) from spring_session where principal_name = ?", accountId)).isEqualTo(1);
    }

    @Test
    void the_hint_from_login_time_still_logs_out_after_a_refresh_and_after_a_silent_authentication() throws Exception {
        // KT-13: làm mới cấp ID token mới; xác thực im lặng đổi bản ghi ủy quyền; hint lúc đăng nhập vẫn dùng được
        newBrowser();
        String accountId = registerAccount();
        JsonNode first = exchange(loginAndGetCode());
        String loginTimeHint = first.path("id_token").asString();
        String sid = claims(first.path("access_token").asString()).path("sid").asString();
        JsonNode refreshed = token("grant_type=refresh_token&refresh_token=" + enc(first.path("refresh_token").asString())
                + "&sid=" + enc(sid));
        assertThat(refreshed.path("id_token").asString()).isNotEqualTo(loginTimeHint);
        exchange(codeFrom(get(authorizeUrl("again")), "again"));       // bản ghi ủy quyền thay bằng bản mới

        HttpResponse<String> response = logout(loginTimeHint, POST_LOGOUT_URI, "late");

        assertThat(location(response)).startsWith(POST_LOGOUT_URI);
        assertThat(loginSessions(accountId)).isZero();
        assertThat(authorizations(accountId)).isZero();
    }

    @Test
    void a_hint_of_another_session_does_not_end_the_session_of_the_cookie() throws Exception {
        // KT-19
        newBrowser();
        String accountId = registerAccount();
        exchange(loginAndGetCode());
        CookieManager jarA = cookies; HttpClient httpA = http;
        newBrowser();
        JsonNode b = exchange(loginAndGetCode());
        assertThat(loginSessions(accountId)).isEqualTo(2);

        cookies = jarA; http = httpA;                                   // cookie của A, hint của B
        HttpResponse<String> response = logout(b.path("id_token").asString(), POST_LOGOUT_URI, "mix");

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(loginSessions(accountId)).isEqualTo(2);
    }

    @Test
    void two_devices_of_one_account_log_out_independently_even_after_a_silent_authentication_on_the_older_one() throws Exception {
        // KT-20 (R13): A đăng nhập, B đăng nhập, A xin mã lại, A đăng xuất
        newBrowser();
        String accountId = registerAccount();
        JsonNode a1 = exchange(loginAndGetCode());
        CookieManager jarA = cookies; HttpClient httpA = http;
        newBrowser();
        exchange(loginAndGetCode());
        CookieManager jarB = cookies; HttpClient httpB = http;
        cookies = jarA; http = httpA;
        JsonNode a2 = exchange(codeFrom(get(authorizeUrl("again")), "again"));

        HttpResponse<String> response = logout(a2.path("id_token").asString(), POST_LOGOUT_URI, "a");

        assertThat(location(response)).startsWith(POST_LOGOUT_URI);
        assertThat(loginSessions(accountId)).isEqualTo(1);              // chỉ B còn
        cookies = jarB; http = httpB;
        assertThat(location(get(authorizeUrl("b-still")))).contains("code=");   // B vẫn đăng nhập (đăng nhập chung)
        assertThat(a1.path("id_token").asString()).isNotEmpty();
    }

    @Test
    void a_code_issued_before_the_logout_cannot_be_exchanged_after_it() throws Exception {
        // KT-16
        newBrowser();
        String accountId = registerAccount();
        JsonNode first = exchange(loginAndGetCode());
        String pendingCode = codeFrom(get(authorizeUrl("pending")), "pending");   // mã cấp rồi, chưa đổi
        logout(first.path("id_token").asString(), POST_LOGOUT_URI, "x");

        HttpResponse<String> late = tokenResponse("grant_type=authorization_code&code=" + enc(pendingCode)
                + "&redirect_uri=" + enc(REDIRECT_URI) + "&code_verifier=" + CODE_VERIFIER);

        assertThat(late.statusCode()).isEqualTo(400);
        assertThat(late.body()).contains("invalid_grant").doesNotContain("access_token");
        assertThat(loginSessions(accountId)).isZero();
    }

    @Test
    void the_applications_are_told_only_after_the_transaction_has_committed() throws Exception {
        // KT-07: lúc báo, phiên và bản ghi ủy quyền đã mất khỏi cơ sở dữ liệu
        newBrowser();
        String accountId = registerAccount();
        JsonNode tokens = exchange(loginAndGetCode());
        java.util.concurrent.atomic.AtomicReference<int[]> seen = new java.util.concurrent.atomic.AtomicReference<>();
        org.mockito.Mockito.doAnswer(call -> {
            seen.set(new int[]{loginSessions(accountId), authorizations(accountId)});
            return null;
        }).when(webGatewayRevocationClient).revoke(org.mockito.ArgumentMatchers.anyString());

        logout(tokens.path("id_token").asString(), POST_LOGOUT_URI, "after-commit");

        assertThat(seen.get()).containsExactly(0, 0);
    }

    @Test
    void a_failure_telling_the_applications_does_not_fail_the_logout_and_leaves_one_warning() throws Exception {
        // KT-07
        newBrowser();
        String accountId = registerAccount();
        JsonNode tokens = exchange(loginAndGetCode());
        org.mockito.Mockito.doThrow(new IllegalStateException("bff down")).when(webGatewayRevocationClient)
                .revoke(org.mockito.ArgumentMatchers.anyString());

        HttpResponse<String> response = logout(tokens.path("id_token").asString(), POST_LOGOUT_URI, "bff-down");

        assertThat(location(response)).startsWith(POST_LOGOUT_URI);
        assertThat(loginSessions(accountId)).isZero();
        awaitLogLine(node -> node.toString().contains("could not tell the applications"));
    }

    @Test
    void the_hint_never_reaches_the_service_log() throws Exception {
        // KT-17
        newBrowser();
        registerAccount();
        JsonNode tokens = exchange(loginAndGetCode());
        String hint = tokens.path("id_token").asString();
        logout(hint, POST_LOGOUT_URI, "logline");

        awaitLogLine(node -> node.toString().contains("/connect/logout"));
        String log = java.nio.file.Files.readString(java.nio.file.Path.of(LOG_FILE));
        assertThat(log).doesNotContain(hint).doesNotContain(hint.substring(hint.indexOf('.') + 1, hint.indexOf('.') + 40));
    }

    @Test
    void a_request_from_an_unregistered_client_or_with_another_redirect_uri_is_refused_and_no_code_is_issued() throws Exception {
        // KT-32
        newBrowser();
        registerAccount();
        String evil = "http://evil.example/callback";

        // chưa đăng nhập: bị từ chối ngay ở bước kiểm tham số (trước cả khi chuyển sang đăng nhập)
        assertNoCodeWasIssuedTo(get(authorizeUrlFor("khong-co-client", REDIRECT_URI)), REDIRECT_URI);
        assertNoCodeWasIssuedTo(get(authorizeUrlFor(CLIENT_ID, evil)), evil);

        // đã có phiên đăng nhập (đường SSO): vẫn không cấp mã cho client lạ hay redirect lạ
        loginAndGetCode();
        int authorizationsBefore = jdbc.queryForObject("select count(*) from oauth2_authorization where principal_name in "
                + "(select id::text from user_accounts where email = ?)", Integer.class, email);
        assertNoCodeWasIssuedTo(get(authorizeUrlFor("khong-co-client", REDIRECT_URI)), REDIRECT_URI);
        assertNoCodeWasIssuedTo(get(authorizeUrlFor(CLIENT_ID, evil)), evil);
        assertThat(jdbc.queryForObject("select count(*) from oauth2_authorization where principal_name in "
                + "(select id::text from user_accounts where email = ?)", Integer.class, email)).isEqualTo(authorizationsBefore);
    }

    @Test
    void an_authorization_code_cannot_be_used_twice_and_the_replay_leaves_our_session_untouched() throws Exception {
        newBrowser();
        String accountId = registerAccount();
        String code = loginAndGetCode();
        String form = "grant_type=authorization_code&code=" + enc(code) + "&redirect_uri=" + enc(REDIRECT_URI)
                + "&code_verifier=" + CODE_VERIFIER;
        assertThat(tokenResponse(form).statusCode()).isEqualTo(200);
        Map<String, Object> sessionBefore = jdbc.queryForMap("select id::text as id, idp_session_id, expires_at from login_sessions where user_account_id = ?::uuid", accountId);
        List<Map<String, Object>> clientSessionsBefore = jdbc.queryForList(
                "select id::text as id, authorization_id from client_sessions where login_session_id = ?::uuid", sessionBefore.get("id"));
        assertThat(clientSessionsBefore).hasSize(1);

        HttpResponse<String> replay = tokenResponse(form);

        // KT-33: bị từ chối, và phiên của ta không đổi: không thêm phiên nào, không kết thúc phiên nào, liên kết giữ nguyên
        assertThat(replay.statusCode()).isEqualTo(400);
        assertThat(replay.body()).contains("invalid_grant");
        assertThat(jdbc.queryForObject("select count(*) from login_sessions where user_account_id = ?::uuid", Integer.class, accountId)).isEqualTo(1);
        assertThat(jdbc.queryForMap("select id::text as id, idp_session_id, expires_at from login_sessions where user_account_id = ?::uuid", accountId))
                .isEqualTo(sessionBefore);
        assertThat(jdbc.queryForList("select id::text as id, authorization_id from client_sessions where login_session_id = ?::uuid", sessionBefore.get("id")))
                .isEqualTo(clientSessionsBefore);
    }

    @Test
    void the_client_secret_comes_from_configuration_and_is_stored_hashed() throws Exception {
        newBrowser();

        String stored = jdbc.queryForObject("select client_secret from oauth2_registered_client where client_id = ?", String.class, CLIENT_ID);

        assertThat(stored).startsWith("$2a$").doesNotContain(CLIENT_SECRET);   // BCrypt, không phải {noop}
        assertThat(jdbc.queryForObject("select client_settings from oauth2_registered_client where client_id = ?", String.class, CLIENT_ID))
                .contains("\"settings.client.require-proof-key\":true");
    }

    @Test
    void the_register_api_needs_no_csrf_token_because_it_carries_no_session_cookie() throws Exception {
        newBrowser();

        registerAccount();     // POST JSON không có _csrf: phải 201
    }

    @Test
    void the_login_page_no_longer_computes_or_sends_a_browser_fingerprint() throws Exception {
        newBrowser();

        String page = get(base() + "/login").body();

        // mã thiết bị là cookie DEVICE_KEY do máy chủ cấp; vân tay do trình duyệt tính không đáng tin (ADR-0005)
        assertThat(page).doesNotContain("device_hash").doesNotContain("crypto.subtle").doesNotContain("canvas").doesNotContain("dh=");
    }

    @Test
    void the_login_page_issues_a_device_key_cookie_before_any_password_is_sent() throws Exception {
        newBrowser();

        HttpResponse<String> page = get(base() + "/login");

        String setCookie = page.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("DEVICE_KEY=")).findFirst().orElseThrow();
        assertThat(setCookie).contains("HttpOnly", "SameSite=Lax", "Path=/", "Max-Age=31536000");
        String issued = setCookie.substring("DEVICE_KEY=".length(), setCookie.indexOf(';'));
        assertThat(issued).hasSizeGreaterThanOrEqualTo(40);
        // lần ghé thứ hai không đổi mã
        HttpResponse<String> again = get(base() + "/login");
        assertThat(again.headers().allValues("Set-Cookie")).noneMatch(c -> c.startsWith("DEVICE_KEY="));

        // đăng nhập xong thì phiên chỉ giữ bản băm của đúng mã đã cấp
        registerAccount();
        get(authorizeUrl("x"));
        location(postLogin(email, PASSWORD));
        String expectedHash = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(issued.getBytes(StandardCharsets.UTF_8)));
        assertThat(jdbc.queryForObject("select device_key from login_sessions where user_account_id in (select id from user_accounts where email = ?)",
                String.class, email)).isEqualTo(expectedHash);
    }

    @Test
    void the_raw_device_key_is_stored_nowhere_neither_in_session_attributes_nor_in_authorizations() throws Exception {
        newBrowser();
        registerAccount();
        String raw = "raw-device-cookie-" + UUID.randomUUID();
        HttpCookie device = new HttpCookie("DEVICE_KEY", raw);
        device.setPath("/");
        cookies.getCookieStore().add(URI.create(base()), device);

        JsonNode tokens = exchange(loginAndGetCode());

        // ProviderManager chép details của yêu cầu sang token đã xác thực nếu kết quả không có: bộ xác thực phải chặn việc đó
        assertThat(jdbc.queryForObject(
                "select count(*) from spring_session_attributes where position(convert_to(?, 'UTF8') in attribute_bytes) > 0",
                Integer.class, raw)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from oauth2_authorization where attributes like ?", Integer.class,
                "%" + raw + "%")).isZero();
        assertThat(tokens.toString()).doesNotContain(raw);
        assertThat(jdbc.queryForObject("select count(*) from login_sessions where device_key = ?", Integer.class, raw)).isZero();
    }
}
