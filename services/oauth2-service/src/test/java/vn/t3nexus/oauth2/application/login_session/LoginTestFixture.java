package vn.t3nexus.oauth2.application.login_session;

import vn.t3nexus.lib.common.domain.service.IdGenerator;
import vn.t3nexus.oauth2.domain.login_session.ClientSessionId;
import vn.t3nexus.oauth2.domain.login_session.DeviceKeyHash;
import vn.t3nexus.oauth2.domain.login_session.DeviceKeyHasher;
import vn.t3nexus.oauth2.domain.login_session.LoginSession;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionId;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionRepository;
import vn.t3nexus.oauth2.domain.login_session.SessionLifetimePolicy;
import vn.t3nexus.oauth2.domain.login_session.SessionLifetimePolicy.Lifetimes;
import vn.t3nexus.oauth2.domain.user_account.Email;
import vn.t3nexus.oauth2.domain.user_account.PasswordHash;
import vn.t3nexus.oauth2.domain.user_account.PasswordHasher;
import vn.t3nexus.oauth2.domain.user_account.RawPassword;
import vn.t3nexus.oauth2.domain.user_account.RegistrationMethod;
import vn.t3nexus.oauth2.domain.user_account.Role;
import vn.t3nexus.oauth2.domain.user_account.UserAccount;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;
import vn.t3nexus.oauth2.domain.user_account.UserAccountRepository;
import vn.t3nexus.oauth2.domain.user_account.UserAccountStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cổng giả dùng chung cho test use case của phiên đăng nhập: kho tài khoản và kho phiên trong bộ nhớ, bộ băm giả có đếm số
 * lần kiểm, đồng hồ cố định. Không chạm cơ sở dữ liệu.
 */
public class LoginTestFixture {

    public static final long    UUID_MSB = 0x01938a5e1c007000L;
    public static final Instant NOW      = Instant.parse("2026-10-13T03:00:00Z");

    public static final UUID USER_ID    = new UUID(UUID_MSB, 0x1001);
    public static final UUID ADMIN_ID   = new UUID(UUID_MSB, 0x1002);
    public static final UUID PENDING_ID = new UUID(UUID_MSB, 0x1003);

    public final Map<UserAccountId, UserAccount>   accounts = new HashMap<>();
    public final Map<LoginSessionId, LoginSession> sessions = new HashMap<>();
    public final AtomicInteger verifyCalls = new AtomicInteger();
    /** Tiền tố của giá trị băm "hệ số cũ" của bộ băm giả: kiểm đúng nhưng {@code needsRehash} là true. */
    public static final String LEGACY_PREFIX = "legacy-hashed:";

    public final UserAccountRepository  accountRepository;
    public final LoginSessionRepository sessionRepository;
    public final PasswordHasher         passwordHasher;
    public final DeviceKeyHasher        deviceKeyHasher = raw -> new DeviceKeyHash("h(" + raw + ")");
    public final SessionLifetimePolicy  lifetimePolicy = new SessionLifetimePolicy(
            new Lifetimes(Duration.ofMinutes(15), Duration.ofDays(7), Duration.ofDays(30)),
            new Lifetimes(Duration.ofMinutes(15), Duration.ofMinutes(30), Duration.ofHours(10)));
    public final IdGenerator            ids;
    public final Clock                  clock = Clock.fixed(NOW, ZoneOffset.UTC);

    public LoginTestFixture() {
        accounts.put(UserAccountId.of(USER_ID), UserAccount.register(UserAccountId.of(USER_ID),
                Email.of("nguyen@example.com"), PasswordHash.ofHashed("hashed:matkhau123"), NOW));
        accounts.put(UserAccountId.of(ADMIN_ID), UserAccount.reconstitute(UserAccountId.of(ADMIN_ID),
                Email.of("admin@t3nexus.com.vn"), PasswordHash.ofHashed("hashed:quantri123"), Role.ADMIN,
                RegistrationMethod.CREDENTIAL, UserAccountStatus.ACTIVE, false, NOW, NOW));
        accounts.put(UserAccountId.of(PENDING_ID), UserAccount.reconstitute(UserAccountId.of(PENDING_ID),
                Email.of("pending@example.com"), PasswordHash.ofHashed("hashed:matkhau123"), Role.USER,
                RegistrationMethod.CREDENTIAL, UserAccountStatus.PENDING, false, NOW, NOW));
        accounts.values().forEach(UserAccount::clearDomainEvents);

        accountRepository = new UserAccountRepository() {
            public Optional<UserAccount> findById(UserAccountId id) { return Optional.ofNullable(accounts.get(id)); }
            public Optional<UserAccount> findByEmail(String email) {
                return accounts.values().stream().filter(a -> a.getEmail().value().equals(email)).findFirst();
            }
            public boolean existsByEmail(Email email) {
                return accounts.values().stream().anyMatch(a -> a.getEmail().equals(email));
            }
            public void save(UserAccount account) { accounts.put(account.getId(), account); }
            public void delete(UserAccountId id) { accounts.remove(id); }
        };
        sessionRepository = new LoginSessionRepository() {
            public Optional<LoginSession> findById(LoginSessionId id) { return Optional.ofNullable(sessions.get(id)); }
            public void save(LoginSession session) { sessions.put(session.getId(), session); }
            public void delete(LoginSessionId id) { sessions.remove(id); }
            public Optional<LoginSession> findByClientSessionId(ClientSessionId sid) {
                return sessions.values().stream()
                        .filter(s -> s.clientSessions().stream().anyMatch(c -> c.getId().equals(sid))).findFirst();
            }
            public Optional<String> findAuthorizationIdByClientSessionId(ClientSessionId sid) {
                return findByClientSessionId(sid).flatMap(s -> s.clientSessions().stream()
                        .filter(c -> c.getId().equals(sid)).map(c -> c.authorizationId()).findFirst());
            }
            public List<LoginSession> findActiveByAccount(UserAccountId accountId, Instant now) {
                return sessions.values().stream()
                        .filter(s -> s.userAccountId().equals(accountId) && s.confirm(now)).toList();
            }
            public List<LoginSession> findActiveByAccountAndDevice(UserAccountId accountId, DeviceKeyHash key, Instant now) {
                return findActiveByAccount(accountId, now).stream().filter(s -> key.equals(s.deviceKey())).toList();
            }
            public List<LoginSession> findExpired(Instant now, int limit) {
                return sessions.values().stream().filter(s -> !s.confirm(now)).limit(limit).toList();
            }
        };
        passwordHasher = new PasswordHasher() {
            public PasswordHash hash(RawPassword raw) { return PasswordHash.ofHashed("hashed:" + raw.value()); }
            public boolean verify(String attempt, PasswordHash hash) {
                verifyCalls.incrementAndGet();
                return hash.getHashedValue().equals("hashed:" + attempt) || hash.getHashedValue().equals(LEGACY_PREFIX + attempt);
            }
            public boolean needsRehash(PasswordHash hash) { return hash.getHashedValue().startsWith(LEGACY_PREFIX); }
        };
        AtomicLong seq = new AtomicLong(0x2000);
        ids = () -> new UUID(UUID_MSB, seq.incrementAndGet());
        verifyCalls.set(0); // dựng use case có thể băm một giá trị giả; chỉ đếm từ lúc đăng nhập
    }
}
