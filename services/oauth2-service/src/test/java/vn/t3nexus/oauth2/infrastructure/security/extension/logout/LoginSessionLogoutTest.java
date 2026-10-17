package vn.t3nexus.oauth2.infrastructure.security.extension.logout;

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
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.t3nexus.lib.common.domain.service.UuidV7IdGenerator;
import vn.t3nexus.oauth2.application.login_session.end_login_session.EndLoginSession;
import vn.t3nexus.oauth2.domain.login_session.ClientId;
import vn.t3nexus.oauth2.domain.login_session.ClientSessionId;
import vn.t3nexus.oauth2.domain.login_session.DeviceKeyHash;
import vn.t3nexus.oauth2.domain.login_session.LoginContext;
import vn.t3nexus.oauth2.domain.login_session.LoginSession;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionId;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionRepository;
import vn.t3nexus.oauth2.domain.user_account.Email;
import vn.t3nexus.oauth2.domain.user_account.PasswordHash;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phần có giao dịch của đăng xuất trên Postgres thật và {@code JdbcOAuth2AuthorizationService}: xóa phiên và bản ghi ủy quyền
 * cùng một giao dịch, lỗi giữa chừng quay lui cả hai (KT-02), hai đăng xuất đồng thời cho một phiên (KT-08).
 */
@SpringBootTest(classes = LoginSessionLogoutTest.TestConfig.class)
class LoginSessionLogoutTest {

    private static final Instant NOW = Instant.parse("2026-10-17T03:00:00Z");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresContainerHolder.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", PostgresContainerHolder.POSTGRES::getUsername);
        registry.add("spring.datasource.password", PostgresContainerHolder.POSTGRES::getPassword);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "10");
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
    @Autowired TransactionTemplate transactions;
    @Autowired DataSource dataSource;

    private JdbcTemplate jdbc;
    private RegisteredClient client;
    private UserAccountId account;
    private LoginSessionId sessionId;
    private String idpSessionId;
    private ClientSessionId sidShop;
    private ClientSessionId sidSeller;
    private EndLoginSession endLoginSession;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        client = RegisteredClient.withId(UUID.randomUUID().toString()).clientId("c-" + UUID.randomUUID())
                .clientSecret("{noop}" + UUID.randomUUID()).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/cb").build();
        clients.save(client);

        account = UserAccountId.of(ids.generate());
        UserAccount registered = UserAccount.register(account, Email.of("lo" + UUID.randomUUID() + "@example.com"),
                PasswordHash.ofHashed("$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234"), NOW);
        transactions.executeWithoutResult(status -> accounts.save(registered));

        sessionId = LoginSessionId.of(ids.generate());
        idpSessionId = "IDP-" + UUID.randomUUID();
        sidShop = ClientSessionId.of(ids.generate());
        sidSeller = ClientSessionId.of(ids.generate());
        // hai bản ghi ủy quyền thật, mỗi phiên ứng dụng trỏ tới một bản
        jdbcService.save(authorization("auth-shop-" + sessionId.getValueAsString()));
        jdbcService.save(authorization("auth-seller-" + sessionId.getValueAsString()));
        LoginSession session = LoginSession.open(sessionId, account, new DeviceKeyHash("d"), idpSessionId,
                new LoginContext("203.0.113.7", "Mozilla/5.0"), NOW.plus(Duration.ofDays(30)), NOW);
        session.issueClientSession(new ClientId("shop-web"), sidShop, "auth-shop-" + sessionId.getValueAsString(), "jti-1", NOW);
        session.issueClientSession(new ClientId("seller-web"), sidSeller, "auth-seller-" + sessionId.getValueAsString(), "jti-2", NOW);
        transactions.executeWithoutResult(status -> sessions.save(session));

        endLoginSession = new EndLoginSession(sessions, Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from oauth2_authorization where registered_client_id = ?", client.getId());
        jdbc.update("delete from login_sessions where id = ?", sessionId.getValue());
        jdbc.update("delete from user_accounts where id = ?", account.getValue());
        jdbc.update("delete from oauth2_registered_client where id = ?", client.getId());
    }

    private OAuth2Authorization authorization(String id) {
        return OAuth2Authorization.withRegisteredClient(client).id(id).principalName(account.getValueAsString())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(new OAuth2AuthorizationCode("code-" + id, NOW, NOW.plusSeconds(300))).build();
    }

    private int loginSessionRows() {
        return jdbc.queryForObject("select count(*) from login_sessions where id = ?", Integer.class, sessionId.getValue());
    }

    private int clientSessionRows() {
        return jdbc.queryForObject("select count(*) from client_sessions where login_session_id = ?", Integer.class, sessionId.getValue());
    }

    private int authorizationRows() {
        return jdbc.queryForObject("select count(*) from oauth2_authorization where registered_client_id = ?", Integer.class, client.getId());
    }

    // ───────────── KT-01, 02: thành công ─────────────

    @Test
    void a_logout_removes_the_session_its_client_sessions_and_every_authorization_in_one_go() {
        LoginSessionLogout logout = new LoginSessionLogout(endLoginSession, jdbcService, transactions);

        EndLoginSession.Result result = logout.end(idpSessionId, sidShop.getValueAsString());

        assertThat(result.ended()).isTrue();
        assertThat(result.endedClientSessions()).hasSize(2);
        assertThat(loginSessionRows()).isZero();
        assertThat(clientSessionRows()).isZero();
        assertThat(authorizationRows()).isZero();
    }

    // ───────────── KT-02: quay lui ─────────────

    @Test
    void a_failure_while_removing_an_authorization_rolls_back_the_session_deletion_too() {
        OAuth2AuthorizationService failingOnSecond = new OAuth2AuthorizationService() {
            private int removed;
            public void save(OAuth2Authorization authorization) { jdbcService.save(authorization); }
            public void remove(OAuth2Authorization authorization) {
                if (++removed == 2) throw new IllegalStateException("database down");
                jdbcService.remove(authorization);
            }
            public OAuth2Authorization findById(String id) { return jdbcService.findById(id); }
            public OAuth2Authorization findByToken(String token, OAuth2TokenType type) { return jdbcService.findByToken(token, type); }
        };
        LoginSessionLogout logout = new LoginSessionLogout(endLoginSession, failingOnSecond, transactions);

        assertThatThrownBy(() -> logout.end(idpSessionId, sidShop.getValueAsString())).isInstanceOf(IllegalStateException.class);

        assertThat(loginSessionRows()).isEqualTo(1);
        assertThat(clientSessionRows()).isEqualTo(2);
        assertThat(authorizationRows()).isEqualTo(2);       // cả bản ghi đã xóa lần đầu cũng được hoàn lại
    }

    // ───────────── KT-08: đăng xuất đồng thời ─────────────

    @Test
    void parallel_logouts_all_succeed_and_exactly_one_really_ends_the_session() throws Exception {
        LoginSessionLogout logout = new LoginSessionLogout(endLoginSession, jdbcService, transactions);
        int threads = 4;
        CyclicBarrier start = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return logout.end(idpSessionId, sidShop.getValueAsString()).ended();
                }));
            }
            int ended = 0;
            for (Future<Boolean> result : results) if (result.get(60, TimeUnit.SECONDS)) ended++;

            assertThat(ended).isEqualTo(1);
            assertThat(loginSessionRows()).isZero();
            assertThat(authorizationRows()).isZero();
        } finally {
            pool.shutdownNow();
        }
    }
}
