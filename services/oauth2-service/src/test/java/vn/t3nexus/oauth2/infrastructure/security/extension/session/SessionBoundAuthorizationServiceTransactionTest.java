package vn.t3nexus.oauth2.infrastructure.security.extension.session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.t3nexus.lib.common.domain.service.UuidV7IdGenerator;
import vn.t3nexus.oauth2.application.login_session.record_client_session_issued.RecordClientSessionIssued;
import vn.t3nexus.oauth2.application.login_session.resolve_authorization_session.ResolveAuthorizationSession;
import vn.t3nexus.oauth2.domain.login_session.DeviceKeyHash;
import vn.t3nexus.oauth2.domain.login_session.LoginContext;
import vn.t3nexus.oauth2.domain.login_session.LoginSession;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionId;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionRepository;
import vn.t3nexus.oauth2.domain.user_account.Email;
import vn.t3nexus.oauth2.domain.user_account.PasswordHash;
import vn.t3nexus.oauth2.domain.user_account.Role;
import vn.t3nexus.oauth2.domain.user_account.UserAccount;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;
import vn.t3nexus.oauth2.domain.user_account.UserAccountRepository;
import vn.t3nexus.oauth2.infrastructure.PostgresContainerHolder;
import vn.t3nexus.oauth2.infrastructure.adapter.repository.login_session.LoginSessionMapper;
import vn.t3nexus.oauth2.infrastructure.adapter.repository.login_session.LoginSessionRepositoryAdapter;
import vn.t3nexus.oauth2.infrastructure.adapter.repository.user_account.UserAccountMapper;
import vn.t3nexus.oauth2.infrastructure.adapter.repository.user_account.UserAccountRepositoryAdapter;
import vn.t3nexus.oauth2.infrastructure.persistence.login_session.LoginSessionJpaRepository;
import vn.t3nexus.oauth2.infrastructure.persistence.user_account.UserAccountJpaRepository;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Điểm móc `save()` với {@code JdbcOAuth2AuthorizationService} và giao dịch JPA thật (ADR-0003 mục 9): lưu bản ghi ủy quyền,
 * ghi nhận vào phiên và xóa bản ghi cũ cùng một giao dịch, nên lỗi ở bất kỳ bước nào <b>quay lui cả bản ghi ủy quyền</b>
 * (KT-22 phần lưu trữ). Chạy trên Postgres do Testcontainers dựng; không dùng giao dịch của test để thấy được commit và
 * quay lui thật.
 */
@SpringBootTest(classes = SessionBoundAuthorizationServiceTransactionTest.TestConfig.class)
class SessionBoundAuthorizationServiceTransactionTest {

    private static final Instant NOW = Instant.parse("2026-10-14T03:00:00Z");
    private static final String SID_A = "01938a5e-1c00-7000-8000-0000000000a1";
    private static final String SID_B = "01938a5e-1c00-7000-8000-0000000000b1";

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresContainerHolder.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", PostgresContainerHolder.POSTGRES::getUsername);
        registry.add("spring.datasource.password", PostgresContainerHolder.POSTGRES::getPassword);
    }

    @Configuration
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            TransactionAutoConfiguration.class})
    @EntityScan("vn.t3nexus.oauth2")
    @EnableJpaRepositories(basePackageClasses = {UserAccountJpaRepository.class, LoginSessionJpaRepository.class})
    @Import({UserAccountRepositoryAdapter.class, UserAccountMapper.class,
            LoginSessionRepositoryAdapter.class, LoginSessionMapper.class})
    static class TestConfig {
        @Bean JdbcOperations jdbcOperations(DataSource dataSource) { return new JdbcTemplate(dataSource); }

        @Bean RegisteredClientRepository registeredClientRepository(JdbcOperations jdbc) {
            return new JdbcRegisteredClientRepository(jdbc);
        }

        @Bean OAuth2AuthorizationService jdbcAuthorizationService(JdbcOperations jdbc, RegisteredClientRepository clients) {
            return new JdbcOAuth2AuthorizationService(jdbc, clients);
        }

        @Bean TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
            return new TransactionTemplate(manager);
        }
    }

    private final UuidV7IdGenerator ids = new UuidV7IdGenerator();

    @Autowired LoginSessionRepository sessions;
    @Autowired UserAccountRepository  accounts;
    @Autowired RegisteredClientRepository clients;
    @Autowired OAuth2AuthorizationService jdbcService;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired DataSource dataSource;

    private JdbcTemplate jdbc;
    private RegisteredClient client;
    private UserAccountId account;
    private LoginSessionId sessionId;
    private Optional<ResolveAuthorizationSession.Result> gate;
    private Optional<String> requestedSid = Optional.empty();
    private SessionBoundAuthorizationService service;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        client = RegisteredClient.withId(UUID.randomUUID().toString()).clientId("c-" + UUID.randomUUID())
                .clientSecret("{noop}" + UUID.randomUUID()).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/cb").build();
        clients.save(client);

        account = UserAccountId.of(ids.generate());
        UserAccount registered = UserAccount.register(account, Email.of("tx" + UUID.randomUUID() + "@example.com"),
                PasswordHash.ofHashed("$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234"), NOW);
        transactionTemplate.executeWithoutResult(status -> accounts.save(registered));
        sessionId = LoginSessionId.of(ids.generate());
        LoginSession session = LoginSession.open(sessionId, account, new DeviceKeyHash("d"), "IDP-" + UUID.randomUUID(),
                new LoginContext("203.0.113.7", "Mozilla/5.0"), NOW.plus(Duration.ofDays(30)), NOW);
        transactionTemplate.executeWithoutResult(status -> sessions.save(session));

        gate = Optional.of(new ResolveAuthorizationSession.Result(sessionId.getValueAsString(), SID_A,
                NOW.plus(Duration.ofDays(30)), Role.USER, "d"));
        service = new SessionBoundAuthorizationService(jdbcService,
                new RecordClientSessionIssued(sessions, Clock.fixed(NOW, ZoneOffset.UTC)), () -> gate, transactionTemplate,
                new SidAuthorizationLookup(jdbcService, sessions, () -> requestedSid, hint -> Optional.empty()));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from oauth2_authorization where registered_client_id = ?", client.getId());
        jdbc.update("delete from login_sessions where id = ?", sessionId.getValue());
        jdbc.update("delete from user_accounts where id = ?", account.getValue());
        jdbc.update("delete from oauth2_registered_client where id = ?", client.getId());
    }

    // ───────────── dựng bản ghi ủy quyền ─────────────

    private OAuth2Authorization codeStage(String id) {
        return OAuth2Authorization.withRegisteredClient(client).id(id).principalName(account.getValueAsString())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(new OAuth2AuthorizationCode("code-" + id, NOW, NOW.plusSeconds(300))).build();
    }

    private OAuth2Authorization withAccessToken(OAuth2Authorization bound, String jti) {
        LinkedHashMap<String, Object> claims = new LinkedHashMap<>();
        claims.put("jti", jti);
        return OAuth2Authorization.from(bound)
                .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-" + jti, NOW, NOW.plusSeconds(900)),
                        metadata -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claims)).build();
    }

    /** Lưu mã (gắn phiên) rồi lưu token, như hai lần Spring gọi save khi đăng nhập. */
    private void issue(String authorizationId, String jti) {
        service.save(codeStage(authorizationId));
        service.save(withAccessToken(jdbcService.findById(authorizationId), jti));
    }

    private int authorizationRows(String... ids) {
        int count = 0;
        for (String id : ids) {
            count += jdbc.queryForObject("select count(*) from oauth2_authorization where id = ?", Integer.class, id);
        }
        return count;
    }

    private String linkedAuthorization(String sid) {
        return jdbc.queryForObject("select authorization_id from client_sessions where id = ?", String.class, UUID.fromString(sid));
    }

    // ───────────── Thành công ─────────────

    @Test
    void the_first_token_commits_the_authorization_and_the_client_session_together() {
        String auth = "auth-" + UUID.randomUUID();

        issue(auth, "jti-1");

        assertThat(authorizationRows(auth)).isEqualTo(1);
        assertThat(linkedAuthorization(SID_A)).isEqualTo(auth);
    }

    @Test
    void silent_authentication_commits_the_relink_and_the_removal_of_the_old_authorization_together() {
        String first = "auth-" + UUID.randomUUID();
        String second = "auth-" + UUID.randomUUID();
        issue(first, "jti-1");

        issue(second, "jti-2");

        assertThat(authorizationRows(first)).isZero();           // bản ghi cũ bị xóa
        assertThat(authorizationRows(second)).isEqualTo(1);
        assertThat(linkedAuthorization(SID_A)).isEqualTo(second);
        assertThat(jdbc.queryForObject("select count(*) from client_sessions where login_session_id = ?", Integer.class,
                sessionId.getValue())).isEqualTo(1);
    }

    @Test
    void a_refresh_keeps_the_single_authorization_row() {
        String auth = "auth-" + UUID.randomUUID();
        issue(auth, "jti-1");

        service.save(withAccessToken(jdbcService.findById(auth), "jti-2"));

        assertThat(authorizationRows(auth)).isEqualTo(1);
        assertThat(linkedAuthorization(SID_A)).isEqualTo(auth);
    }

    // ───────────── Quay lui (điều quan trọng nhất) ─────────────

    @Test
    void a_sid_mismatch_rolls_back_the_authorization_that_was_just_saved() {
        String first = "auth-" + UUID.randomUUID();
        String second = "auth-" + UUID.randomUUID();
        issue(first, "jti-1");
        // đua nhau: mã mới mang sid khác với phiên ứng dụng đã có
        gate = Optional.of(new ResolveAuthorizationSession.Result(sessionId.getValueAsString(), SID_B,
                NOW.plus(Duration.ofDays(30)), Role.USER, "d"));
        service.save(codeStage(second));
        OAuth2Authorization withToken = withAccessToken(jdbcService.findById(second), "jti-2");

        Throwable thrown = catchThrowable(() -> service.save(withToken));

        assertThat(thrown).isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                e -> assertThat(e.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_GRANT));
        assertThat(jdbcService.findById(second).getAccessToken()).isNull();   // vẫn là bản ghi mã, token không được lưu
        assertThat(authorizationRows(first)).isEqualTo(1);                    // bản ghi cũ còn nguyên
        assertThat(linkedAuthorization(SID_A)).isEqualTo(first);
    }

    @Test
    void a_session_that_is_gone_rolls_back_the_authorization_that_was_just_saved() {
        String auth = "auth-" + UUID.randomUUID();
        service.save(codeStage(auth));
        OAuth2Authorization withToken = withAccessToken(jdbcService.findById(auth), "jti-1");
        jdbc.update("delete from login_sessions where id = ?", sessionId.getValue());   // phiên bị xóa giữa chừng

        Throwable thrown = catchThrowable(() -> service.save(withToken));

        assertThat(thrown).isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                e -> assertThat(e.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_GRANT));
        assertThat(jdbcService.findById(auth).getAccessToken()).isNull();
    }

    // ───────────── Tra cứu trên kho thật (ADR-0003 mục 8) ─────────────

    @Test
    void a_refresh_token_is_found_by_sid_on_the_real_store_after_the_json_round_trip() {
        String auth = "auth-" + UUID.randomUUID();
        String refresh = "refresh-" + UUID.randomUUID();
        service.save(codeStage(auth));
        OAuth2Authorization withTokens = OAuth2Authorization.from(withAccessToken(jdbcService.findById(auth), "jti-1"))
                .refreshToken(new org.springframework.security.oauth2.core.OAuth2RefreshToken(refresh, NOW, NOW.plus(Duration.ofDays(7))))
                .build();
        service.save(withTokens);

        requestedSid = Optional.of(SID_A);
        assertThat(service.findByToken(refresh, org.springframework.security.oauth2.server.authorization.OAuth2TokenType.REFRESH_TOKEN))
                .extracting(OAuth2Authorization::getId).isEqualTo(auth);

        requestedSid = Optional.of(SID_B);
        assertThat(service.findByToken(refresh, org.springframework.security.oauth2.server.authorization.OAuth2TokenType.REFRESH_TOKEN)).isNull();
    }

    @Test
    void the_authorization_code_lookup_has_an_index_to_use() {
        // Bảng nhỏ thì kế hoạch chọn quét tuần tự dù có chỉ mục; tắt quét tuần tự để chứng minh chỉ mục (V14) có sẵn và dùng được.
        transactionTemplate.executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL enable_seqscan = off");
            String plan = String.join("\n", jdbc.queryForList(
                    "explain select id from oauth2_authorization where authorization_code_value = 'some-code'", String.class));
            assertThat(plan).contains("idx_oauth2_authorization_code");
        });
    }
}
