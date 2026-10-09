package vn.t3nexus.oauth2.infrastructure.cross_cutting.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.t3nexus.oauth2.domain.user_account.InternalEmailPolicy;
import vn.t3nexus.oauth2.domain.user_account.UserAccountRepository;
import vn.t3nexus.oauth2.domain.user_account.UserAccountService;

/** Domain Service không mang chú thích khung ứng dụng nên được khai báo bean ở đây. */
@Configuration
public class DomainServiceConfig {

    @Bean
    public UserAccountService userAccountService(UserAccountRepository userAccountRepository,
                                                 InternalEmailPolicy internalEmailPolicy) {
        return new UserAccountService(userAccountRepository, internalEmailPolicy);
    }
}
