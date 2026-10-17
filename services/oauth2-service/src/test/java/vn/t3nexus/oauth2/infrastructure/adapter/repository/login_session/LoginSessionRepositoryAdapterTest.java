package vn.t3nexus.oauth2.infrastructure.adapter.repository.login_session;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.t3nexus.oauth2.infrastructure.PostgresContainerHolder;
import vn.t3nexus.lib.common.domain.service.UuidV7IdGenerator;
import vn.t3nexus.oauth2.application.login_session.authenticate_with_password.AuthenticateWithPassword;
import vn.t3nexus.oauth2.application.login_session.end_login_session.EndLoginSession;
import vn.t3nexus.oauth2.application.login_session.open_login_session.OpenLoginSession;
import vn.t3nexus.oauth2.domain.login_session.ClientId;
import vn.t3nexus.oauth2.domain.login_session.ClientSessionId;
import vn.t3nexus.oauth2.domain.login_session.DeviceKeyHash;
import vn.t3nexus.oauth2.domain.login_session.SessionLifetimePolicy;
import vn.t3nexus.oauth2.domain.user_account.PasswordHasher;
import vn.t3nexus.oauth2.domain.user_account.RawPassword;
import vn.t3nexus.oauth2.domain.user_account.UserAccountException;
import vn.t3nexus.oauth2.domain.user_account.UserAccountErrorCode;
import java.time.Clock;
import java.time.ZoneOffset;
import vn.t3nexus.oauth2.domain.login_session.LoginContext;
import vn.t3nexus.oauth2.domain.login_session.LoginSession;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionErrorCode;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionException;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionId;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionRepository;
import vn.t3nexus.oauth2.domain.user_account.Email;
import vn.t3nexus.oauth2.domain.user_account.PasswordHash;
import vn.t3nexus.oauth2.domain.user_account.UserAccount;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;
import vn.t3nexus.oauth2.domain.user_account.UserAccountRepository;
import vn.t3nexus.oauth2.infrastructure.adapter.repository.user_account.UserAccountMapper;
import vn.t3nexus.oauth2.infrastructure.adapter.repository.user_account.UserAccountRepositoryAdapter;
import vn.t3nexus.oauth2.infrastructure.persistence.login_session.LoginSessionJpaRepository;
import vn.t3nexus.oauth2.infrastructure.persistence.user_account.UserAccountJpaRepository;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Chỉ những ca không thay được bằng test miền: lưu rồi đọc lại, ràng buộc duy nhất của (phiên người dùng, client) ở cơ sở
 * dữ liệu dịch thành lỗi miền, truy vấn "đang sống" lọc theo hạn, xóa kéo theo phiên ứng dụng, truy vấn dùng chỉ mục, và
 * tranh chấp thật giữa nhiều luồng (KT-11, 21, 22, 31 phần lưu trữ). Chạy trên Postgres do Testcontainers dựng, chạy Flyway
 * từ trống nên cũng kiểm migration.
 */
@SpringBootTest(classes = LoginSessionRepositoryAdapterTest.TestConfig.class)
class LoginSessionRepositoryAdapterTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresContainerHolder.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", PostgresContainerHolder.POSTGRES::getUsername);
        registry.add("spring.datasource.password", PostgresContainerHolder.POSTGRES::getPassword);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "20"); // test tranh chấp giữ 16 kết nối cùng lúc
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
    }

    private static final Instant NOW = Instant.parse("2026-10-14T03:00:00Z");

    private final UuidV7IdGenerator ids = new UuidV7IdGenerator();
    private final ClientId shop   = new ClientId("shop-web");
    private final ClientId seller = new ClientId("seller-web");

    @Autowired LoginSessionRepository sessions;
    @Autowired UserAccountRepository  accounts;
    @Autowired EntityManager          entityManager;
    @Autowired DataSource             dataSource;
    @Autowired PlatformTransactionManager transactionManager;

    // ───────────── Hỗ trợ ─────────────

    private UserAccountId newAccount() {
        UserAccountId id = UserAccountId.of(ids.generate());
        accounts.save(UserAccount.register(id, Email.of("u" + UUID.randomUUID() + "@example.com"),
                PasswordHash.ofHashed("$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234"), NOW));
        return id;
    }

    private LoginSession openSession(UserAccountId account, DeviceKeyHash device, Instant issuedAt, Instant expiresAt) {
        LoginSession session = LoginSession.open(LoginSessionId.of(ids.generate()), account, device,
                "IDP-" + UUID.randomUUID(), new LoginContext("203.0.113.7", "Mozilla/5.0"), expiresAt, issuedAt);
        session.clearDomainEvents();
        return session;
    }

    private LoginSession openSession(UserAccountId account) {
        return openSession(account, new DeviceKeyHash("device-hash"), NOW, NOW.plus(Duration.ofDays(30)));
    }

    private ClientSessionId sid() {
        return ClientSessionId.of(ids.generate());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    // ───────────── Lưu rồi đọc lại ─────────────

    @Test
    @Transactional
    void saved_session_reads_back_with_every_field_and_its_client_sessions() {
        UserAccountId account = newAccount();
        LoginSession session = openSession(account);
        ClientSessionId sidShop = sid();
        ClientSessionId sidSeller = sid();
        session.issueClientSession(shop, sidShop, "auth-shop", "jti-1", NOW.plusSeconds(1));
        session.issueClientSession(seller, sidSeller, "auth-seller", "jti-2", NOW.plusSeconds(2));

        sessions.save(session);
        flushAndClear();

        LoginSession loaded = sessions.findById(session.getId()).orElseThrow();
        assertThat(loaded.userAccountId()).isEqualTo(account);
        assertThat(loaded.deviceKey()).isEqualTo(new DeviceKeyHash("device-hash"));
        assertThat(loaded.idpSessionId()).isEqualTo(session.idpSessionId());
        assertThat(loaded.issuedAt()).isEqualTo(NOW);
        assertThat(loaded.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThat(loaded.clientSessions()).hasSize(2);
        assertThat(loaded.clientSessions()).extracting(c -> c.getId()).containsExactly(sidShop, sidSeller);
        assertThat(loaded.clientSessions()).extracting(c -> c.authorizationId()).containsExactly("auth-shop", "auth-seller");
        assertThat(loaded.getDomainEvents()).isEmpty();
    }

    @Test
    @Transactional
    void a_session_without_a_device_key_reads_back_without_one() {
        LoginSession session = openSession(newAccount(), null, NOW, NOW.plus(Duration.ofDays(1)));

        sessions.save(session);
        flushAndClear();

        assertThat(sessions.findById(session.getId()).orElseThrow().deviceKey()).isNull();
    }

    @Test
    @Transactional
    void updating_the_idp_session_id_is_persisted_and_nothing_else_changes() {
        LoginSession session = openSession(newAccount());
        sessions.save(session);
        flushAndClear();

        LoginSession loaded = sessions.findById(session.getId()).orElseThrow();
        loaded.updateIdpSessionId("IDP-ROTATED");
        sessions.save(loaded);
        flushAndClear();

        LoginSession again = sessions.findById(session.getId()).orElseThrow();
        assertThat(again.idpSessionId()).isEqualTo("IDP-ROTATED");
        assertThat(again.expiresAt()).isEqualTo(session.expiresAt());
    }

    // ───────────── Tra theo sid (KT-21) ─────────────

    @Test
    @Transactional
    void lookup_by_sid_returns_the_session_with_all_its_client_sessions() {
        LoginSession session = openSession(newAccount());
        ClientSessionId sidShop = sid();
        session.issueClientSession(shop, sidShop, "auth-shop", "jti-1", NOW);
        session.issueClientSession(seller, sid(), "auth-seller", "jti-2", NOW);
        sessions.save(session);
        flushAndClear();

        LoginSession found = sessions.findByClientSessionId(sidShop).orElseThrow();

        assertThat(found.getId()).isEqualTo(session.getId());
        assertThat(found.clientSessions()).hasSize(2);
    }

    @Test
    @Transactional
    void lookup_by_an_unknown_sid_finds_nothing() {
        sessions.save(openSession(newAccount()));
        flushAndClear();

        assertThat(sessions.findByClientSessionId(sid())).isEmpty();
    }

    // ───────────── Đổi liên kết và ràng buộc duy nhất (KT-22, INV-ATH-10) ─────────────

    @Test
    @Transactional
    void a_relink_is_persisted_and_the_client_still_has_exactly_one_row() {
        LoginSession session = openSession(newAccount());
        ClientSessionId sidShop = sid();
        session.issueClientSession(shop, sidShop, "auth-A", "jti-1", NOW);
        sessions.save(session);
        flushAndClear();

        LoginSession loaded = sessions.findById(session.getId()).orElseThrow();
        loaded.issueClientSession(shop, sid(), "auth-B", "jti-2", NOW.plusSeconds(60));
        sessions.save(loaded);
        flushAndClear();

        LoginSession again = sessions.findById(session.getId()).orElseThrow();
        assertThat(again.clientSessions()).hasSize(1);
        assertThat(again.clientSessions().get(0).getId()).isEqualTo(sidShop);
        assertThat(again.clientSessions().get(0).authorizationId()).isEqualTo("auth-B");
    }

    @Test
    @Transactional
    void two_aggregates_issuing_for_the_same_client_hit_the_unique_constraint_and_it_is_translated() {
        LoginSession base = openSession(newAccount());
        sessions.save(base);
        flushAndClear();

        LoginSession first = sessions.findById(base.getId()).orElseThrow();
        LoginSession second = sessions.findById(base.getId()).orElseThrow();
        first.issueClientSession(shop, sid(), "auth-A", "jti-1", NOW);
        second.issueClientSession(shop, sid(), "auth-B", "jti-2", NOW);

        sessions.save(first);
        assertThatThrownBy(() -> sessions.save(second))
                .isInstanceOfSatisfying(LoginSessionException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(LoginSessionErrorCode.CLIENT_SESSION_CONFLICT));
    }

    @Test
    @Transactional
    void the_same_authorization_id_cannot_belong_to_two_client_sessions() {
        LoginSession session = openSession(newAccount());
        session.issueClientSession(shop, sid(), "auth-A", "jti-1", NOW);
        session.issueClientSession(seller, sid(), "auth-A", "jti-2", NOW);

        assertThatThrownBy(() -> sessions.save(session))
                .isInstanceOfSatisfying(LoginSessionException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(LoginSessionErrorCode.CLIENT_SESSION_CONFLICT));
    }

    // ───────────── Đang sống, quá hạn (KT-31, INV-ATH-08) ─────────────

    @Test
    @Transactional
    void active_sessions_of_an_account_exclude_expired_ones_and_other_accounts() {
        UserAccountId account = newAccount();
        LoginSession live = openSession(account, new DeviceKeyHash("d1"), NOW, NOW.plus(Duration.ofDays(30)));
        LoginSession liveOther = openSession(account, new DeviceKeyHash("d2"), NOW.plusSeconds(5), NOW.plus(Duration.ofDays(1)));
        LoginSession expired = openSession(account, new DeviceKeyHash("d1"), NOW.minus(Duration.ofDays(2)), NOW.minus(Duration.ofDays(1)));
        LoginSession someoneElse = openSession(newAccount());
        live.issueClientSession(shop, sid(), "auth-live", "jti-1", NOW);
        sessions.save(live);
        sessions.save(liveOther);
        sessions.save(expired);
        sessions.save(someoneElse);
        flushAndClear();

        List<LoginSession> active = sessions.findActiveByAccount(account, NOW.plusSeconds(10));

        assertThat(active).extracting(LoginSession::getId).containsExactly(live.getId(), liveOther.getId());
        assertThat(active.get(0).clientSessions()).hasSize(1);
        assertThat(sessions.findActiveByAccountAndDevice(account, new DeviceKeyHash("d1"), NOW.plusSeconds(10)))
                .extracting(LoginSession::getId).containsExactly(live.getId());
    }

    @Test
    @Transactional
    void a_session_whose_expiry_has_arrived_is_not_active() {
        UserAccountId account = newAccount();
        LoginSession session = openSession(account, null, NOW, NOW.plusSeconds(60));
        sessions.save(session);
        flushAndClear();

        assertThat(sessions.findActiveByAccount(account, NOW.plusSeconds(59))).hasSize(1);
        assertThat(sessions.findActiveByAccount(account, NOW.plusSeconds(60))).isEmpty();
    }

    @Test
    @Transactional
    void expired_sessions_come_back_oldest_first_and_respect_the_batch_limit() {
        UserAccountId account = newAccount();
        Instant cutoff = NOW.plus(Duration.ofDays(100));
        LoginSession oldest = openSession(account, null, NOW, NOW.plusSeconds(10));
        LoginSession middle = openSession(account, null, NOW, NOW.plusSeconds(20));
        LoginSession newest = openSession(account, null, NOW, NOW.plusSeconds(30));
        LoginSession alive  = openSession(account, null, NOW, cutoff.plusSeconds(1));
        oldest.issueClientSession(shop, sid(), "auth-old", "jti-1", NOW);
        sessions.save(newest);
        sessions.save(oldest);
        sessions.save(alive);
        sessions.save(middle);
        flushAndClear();

        List<LoginSession> batch = sessions.findExpired(cutoff, 2);

        assertThat(batch).extracting(LoginSession::getId).containsExactly(oldest.getId(), middle.getId());
        assertThat(batch.get(0).clientSessions()).hasSize(1);
        assertThat(sessions.findExpired(cutoff, 10)).extracting(LoginSession::getId)
                .containsExactly(oldest.getId(), middle.getId(), newest.getId());
        assertThat(sessions.findExpired(NOW, 10)).isEmpty();
    }

    @Test
    @Transactional
    void deleting_a_session_removes_its_client_sessions_too() {
        LoginSession session = openSession(newAccount());
        ClientSessionId sidShop = sid();
        session.issueClientSession(shop, sidShop, "auth-A", "jti-1", NOW);
        sessions.save(session);
        flushAndClear();

        sessions.delete(session.getId());
        flushAndClear();

        assertThat(sessions.findById(session.getId())).isEmpty();
        assertThat(sessions.findByClientSessionId(sidShop)).isEmpty();
        assertThat(new JdbcTemplate(dataSource).queryForObject(
                "select count(*) from client_sessions where id = ?", Integer.class, sidShop.getValue())).isZero();
    }

    // ───────────── Truy vấn dùng chỉ mục (KT-21) ─────────────

    @Test
    void the_old_session_table_is_gone_after_the_migrations_and_the_new_ones_exist() {
        // KT-12: mô hình phiên cũ đã gỡ (V16), chạy Flyway từ trống
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThat(jdbc.queryForObject("select to_regclass('oauth_sessions')", String.class)).isNull();
        assertThat(jdbc.queryForObject("select to_regclass('login_sessions')", String.class)).isEqualTo("login_sessions");
        assertThat(jdbc.queryForObject("select to_regclass('client_sessions')", String.class)).isEqualTo("client_sessions");
    }

    @Test
    void the_main_lookups_have_an_index_to_use() {
        // Bảng nhỏ thì kế hoạch chọn quét tuần tự dù có chỉ mục; tắt quét tuần tự để chứng minh chỉ mục có sẵn và dùng được.
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL enable_seqscan = off");
            assertThat(plan(jdbc, "select * from client_sessions where id = '" + UUID.randomUUID() + "'")).contains("Index");
            assertThat(plan(jdbc, "select * from login_sessions where user_account_id = '" + UUID.randomUUID() + "'")).contains("Index");
            assertThat(plan(jdbc, "select * from login_sessions where expires_at <= now() order by expires_at limit 100")).contains("Index");
            assertThat(plan(jdbc, "select * from client_sessions where authorization_id = 'x'")).contains("Index");
            assertThat(plan(jdbc, "select id from login_sessions where idp_session_id = 'x' and expires_at > now() order by issued_at desc limit 1")).contains("Index");
        });
    }

    private static String plan(JdbcTemplate jdbc, String sql) {
        return String.join("\n", jdbc.queryForList("explain " + sql, String.class));
    }

    // ───────────── Tranh chấp thật giữa nhiều luồng (KT-11) ─────────────

    @Test
    void many_threads_issuing_for_the_same_client_really_conflict_and_still_end_with_exactly_one_client_session() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UserAccountId account = tx.execute(status -> newAccount());
        LoginSession base = openSession(account);
        tx.executeWithoutResult(status -> sessions.save(base));

        int threads = 16;
        CyclicBarrier start     = new CyclicBarrier(threads);
        // Ở lần thử đầu mọi luồng đều đã đọc xong (client chưa có phiên) rồi mới cùng ghi: buộc tranh chấp thật, không phụ
        // thuộc tốc độ máy. Các luồng chờ ở đây khi đang giữ kết nối nên pool phải lớn hơn số luồng (xem datasource()).
        CyclicBarrier allLoaded = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final int n = i;
                results.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    int conflicts = 0;
                    for (int attempt = 0; attempt < 5; attempt++) {
                        final boolean firstAttempt = attempt == 0;
                        try {
                            tx.executeWithoutResult(status -> {
                                LoginSession loaded = sessions.findById(base.getId()).orElseThrow();
                                if (firstAttempt) awaitAllLoaded(allLoaded);
                                loaded.issueClientSession(shop, sid(), "auth-" + n, "jti-" + n, NOW);
                                sessions.save(loaded);
                            });
                            return conflicts;
                        } catch (LoginSessionException e) {
                            if (e.getErrorCode() != LoginSessionErrorCode.CLIENT_SESSION_CONFLICT) throw e;
                            conflicts++;
                        }
                    }
                    throw new AssertionError("still conflicting after 5 attempts");
                }));
            }
            int totalConflicts = 0;
            for (Future<Integer> result : results) totalConflicts += result.get(60, TimeUnit.SECONDS);

            // 16 luồng cùng chèn một hàng mới cho cùng (phiên người dùng, client): chỉ một luồng thắng, 15 luồng còn lại
            // vi phạm ràng buộc duy nhất rồi thử lại.
            assertThat(totalConflicts).isGreaterThanOrEqualTo(threads - 1);
            assertThat(jdbc.queryForObject("select count(*) from client_sessions where login_session_id = ?",
                    Integer.class, base.getId().getValue())).isEqualTo(1);
        } finally {
            pool.shutdownNow();
            jdbc.update("delete from login_sessions where id = ?", base.getId().getValue());
            jdbc.update("delete from user_accounts where id = ?", account.getValue());
        }
    }

    // ───────────── Tra theo cookie và khóa hàng (đăng xuất) ─────────────

    @Test
    @Transactional
    void the_lookup_by_cookie_finds_only_a_living_session_with_its_client_sessions() {
        UserAccountId account = newAccount();
        LoginSession alive = openSession(account);
        alive.issueClientSession(shop, sid(), "auth-1", "jti-1", NOW);
        LoginSession expired = openSession(account, new DeviceKeyHash("d2"), NOW.minus(Duration.ofDays(40)), NOW.minus(Duration.ofDays(10)));
        sessions.save(alive);
        sessions.save(expired);
        flushAndClear();

        assertThat(sessions.findActiveByIdpSessionIdForUpdate(alive.idpSessionId(), NOW))
                .get().satisfies(found -> {
                    assertThat(found.getId()).isEqualTo(alive.getId());
                    assertThat(found.clientSessions()).hasSize(1);
                });
        assertThat(sessions.findActiveByIdpSessionIdForUpdate(expired.idpSessionId(), NOW)).isEmpty();
        assertThat(sessions.findActiveByIdpSessionIdForUpdate("IDP-UNKNOWN", NOW)).isEmpty();
    }

    @Test
    @Transactional
    void locking_a_session_that_no_longer_exists_gives_nothing() {
        LoginSession session = openSession(newAccount());
        sessions.save(session);
        flushAndClear();
        sessions.delete(session.getId());
        flushAndClear();

        assertThat(sessions.findByIdForUpdate(session.getId())).isEmpty();
    }

    // ───────────── Đăng xuất đua với cấp phiên ứng dụng (KT-15, R7) ─────────────

    /** Cách điểm móc lúc cấp token nạp phiên trước khi sửa và ghi lại (xem `RecordClientSessionIssued`). */
    private LoginSession loadForIssue(LoginSessionId id) {
        return sessions.findByIdForUpdate(id).orElseThrow();
    }

    @Test
    void a_logout_racing_with_an_issue_never_leaves_a_resurrected_session() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UserAccountId account = tx.execute(status -> newAccount());
        LoginSession base = openSession(account);
        ClientSessionId sidShop = sid();
        base.issueClientSession(shop, sidShop, "auth-shop", "jti-1", NOW);
        tx.executeWithoutResult(status -> sessions.save(base));
        EndLoginSession logout = new EndLoginSession(sessions, Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC));

        CountDownLatch issuerLoaded = new CountDownLatch(1);
        CountDownLatch logoutDone   = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // Bên cấp nạp phiên rồi chờ đăng xuất xong (chờ có giới hạn: nếu nạp có khóa hàng thì đăng xuất bị chặn và bên cấp
            // cứ đi tiếp sau thời gian chờ). Cả hai thứ tự kết thúc đều phải cho cùng kết quả cuối: phiên không còn.
            Future<?> issuer = pool.submit(() -> tx.executeWithoutResult(status -> {
                LoginSession loaded = loadForIssue(base.getId());
                issuerLoaded.countDown();
                awaitQuietly(logoutDone, 1500);
                loaded.issueClientSession(seller, sid(), "auth-seller", "jti-2", NOW.plusSeconds(60));
                sessions.save(loaded);
            }));
            assertThat(issuerLoaded.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> loggingOut = pool.submit(() -> {
                tx.executeWithoutResult(status ->
                        logout.handle(new EndLoginSession.Command(base.idpSessionId(), sidShop.getValueAsString())));
                logoutDone.countDown();
            });

            Throwable issuerFailure = failureOf(issuer);
            loggingOut.get(30, TimeUnit.SECONDS);

            assertThat(jdbc.queryForObject("select count(*) from login_sessions where id = ?",
                    Integer.class, base.getId().getValue())).as("login session resurrected").isZero();
            assertThat(jdbc.queryForObject("select count(*) from client_sessions where login_session_id = ?",
                    Integer.class, base.getId().getValue())).as("client session resurrected").isZero();
            if (issuerFailure != null) assertThat(issuerFailure).isInstanceOf(LoginSessionException.class);
        } finally {
            pool.shutdownNow();
            jdbc.update("delete from login_sessions where id = ?", base.getId().getValue());
            jdbc.update("delete from user_accounts where id = ?", account.getValue());
        }
    }

    private static void awaitQuietly(CountDownLatch latch, long millis) {
        try {
            latch.await(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Throwable failureOf(Future<?> future) throws Exception {
        try {
            future.get(30, TimeUnit.SECONDS);
            return null;
        } catch (ExecutionException e) {
            return e.getCause();
        }
    }

    private static void awaitAllLoaded(CyclicBarrier barrier) {
        try {
            barrier.await(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("not every thread reached the barrier", e);
        }
    }

    // ───────────── Hai use case đăng nhập trên kho thật (bước 6 chạy lại trên Postgres) ─────────────

    private PasswordHasher hasher() {
        return new PasswordHasher() {
            public PasswordHash hash(RawPassword raw) { return PasswordHash.ofHashed("hashed:" + raw.value()); }
            public boolean verify(String attempt, PasswordHash hash) { return hash.getHashedValue().equals("hashed:" + attempt); }
        };
    }

    private AuthenticateWithPassword authenticateOnRealStore() {
        return new AuthenticateWithPassword(accounts, hasher(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private OpenLoginSession openOnRealStore() {
        SessionLifetimePolicy policy = new SessionLifetimePolicy(
                new SessionLifetimePolicy.Lifetimes(Duration.ofMinutes(15), Duration.ofDays(7), Duration.ofDays(30)),
                new SessionLifetimePolicy.Lifetimes(Duration.ofMinutes(15), Duration.ofMinutes(30), Duration.ofHours(10)));
        return new OpenLoginSession(accounts, sessions, raw -> new DeviceKeyHash("h(" + raw + ")"), policy, ids,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private String registerAccount(String password) {
        String email = "login" + UUID.randomUUID() + "@example.com";
        accounts.save(UserAccount.register(UserAccountId.of(ids.generate()), Email.of(email),
                PasswordHash.ofHashed("hashed:" + password), NOW));
        return email;
    }

    @Test
    @Transactional
    void a_correct_login_persists_one_user_session_with_hashed_device_key_and_no_client_session() {
        String email = registerAccount("matkhau123");

        AuthenticateWithPassword.Result authenticated = authenticateOnRealStore().handle(new AuthenticateWithPassword.Command(
                email, "matkhau123", new DeviceKeyHash("h(raw-device-cookie)"), "203.0.113.7", "Mozilla/5.0"));
        OpenLoginSession.Result opened = openOnRealStore().handle(new OpenLoginSession.Command(
                authenticated.userAccountId().getValueAsString(), "raw-device-cookie", "203.0.113.7", "Mozilla/5.0", "IDP-1"));
        flushAndClear();

        LoginSession stored = sessions.findById(LoginSessionId.of(opened.loginSessionId())).orElseThrow();
        assertThat(stored.userAccountId()).isEqualTo(authenticated.userAccountId());
        assertThat(stored.deviceKey()).isEqualTo(new DeviceKeyHash("h(raw-device-cookie)"));
        assertThat(stored.idpSessionId()).isEqualTo("IDP-1");
        assertThat(stored.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThat(stored.clientSessions()).isEmpty();
        assertThat(new JdbcTemplate(dataSource).queryForObject(
                "select count(*) from login_sessions where device_key = 'raw-device-cookie'", Integer.class)).isZero();
    }

    @Test
    @Transactional
    void a_rejected_authentication_opens_nothing() {
        String email = registerAccount("matkhau123");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer before = jdbc.queryForObject("select count(*) from login_sessions", Integer.class);

        assertThatThrownBy(() -> authenticateOnRealStore().handle(new AuthenticateWithPassword.Command(
                email, "sai-mat-khau-1", new DeviceKeyHash("h(raw-device-cookie)"), "203.0.113.7", "Mozilla/5.0")))
                .isInstanceOfSatisfying(UserAccountException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UserAccountErrorCode.INVALID_CREDENTIALS));

        assertThat(jdbc.queryForObject("select count(*) from login_sessions", Integer.class)).isEqualTo(before);
    }
}
