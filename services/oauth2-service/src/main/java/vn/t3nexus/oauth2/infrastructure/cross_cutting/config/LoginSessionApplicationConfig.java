package vn.t3nexus.oauth2.infrastructure.cross_cutting.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionOperations;
import vn.t3nexus.lib.common.domain.service.IdGenerator;
import vn.t3nexus.oauth2.application.login_session.authenticate_with_password.AuthenticateWithPassword;
import vn.t3nexus.oauth2.application.login_session.open_login_session.OpenLoginSession;
import vn.t3nexus.oauth2.application.login_session.record_client_session_issued.RecordClientSessionIssued;
import vn.t3nexus.oauth2.application.login_session.resolve_authorization_session.ResolveAuthorizationSession;
import vn.t3nexus.oauth2.domain.login_session.DeviceKeyHasher;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionRepository;
import vn.t3nexus.oauth2.domain.login_session.SessionLifetimePolicy;
import vn.t3nexus.oauth2.domain.user_account.PasswordHasher;
import vn.t3nexus.oauth2.domain.user_account.UserAccountRepository;

import java.time.Clock;

/**
 * Khai báo bean cho các use case của phiên đăng nhập (feature 02). Các use case là lớp thuần, không mang chú thích khung ứng
 * dụng, nên được dựng ở đây (cùng cách với {@link DomainServiceConfig}). Giao dịch của điểm móc lúc token được cấp do lớp bọc
 * {@code LoginSessionAuthorizationService} mở bằng {@link TransactionOperations}, không phải ở các use case này.
 */
@Configuration
public class LoginSessionApplicationConfig {

    @Bean
    public AuthenticateWithPassword authenticateWithPassword(UserAccountRepository userAccountRepository,
                                                             PasswordHasher passwordHasher, Clock clock) {
        return new AuthenticateWithPassword(userAccountRepository, passwordHasher, clock);
    }

    @Bean
    public OpenLoginSession openLoginSession(UserAccountRepository userAccountRepository,
                                             LoginSessionRepository loginSessionRepository,
                                             DeviceKeyHasher deviceKeyHasher, SessionLifetimePolicy lifetimePolicy,
                                             IdGenerator idGenerator, Clock clock) {
        return new OpenLoginSession(userAccountRepository, loginSessionRepository, deviceKeyHasher, lifetimePolicy,
                idGenerator, clock);
    }

    @Bean
    public ResolveAuthorizationSession resolveAuthorizationSession(LoginSessionRepository loginSessionRepository,
                                                                   UserAccountRepository userAccountRepository,
                                                                   IdGenerator idGenerator, Clock clock) {
        return new ResolveAuthorizationSession(loginSessionRepository, userAccountRepository, idGenerator, clock);
    }

    @Bean
    public RecordClientSessionIssued recordClientSessionIssued(LoginSessionRepository loginSessionRepository, Clock clock) {
        return new RecordClientSessionIssued(loginSessionRepository, clock);
    }
}
