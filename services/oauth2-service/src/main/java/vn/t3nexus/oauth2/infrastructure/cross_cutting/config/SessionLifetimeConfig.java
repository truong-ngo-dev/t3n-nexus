package vn.t3nexus.oauth2.infrastructure.cross_cutting.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.t3nexus.oauth2.domain.login_session.SessionLifetimePolicy;
import vn.t3nexus.oauth2.infrastructure.security.session.DeviceKeyCookieProperties;

@Configuration
@EnableConfigurationProperties({SessionLifetimeProperties.class,
        vn.t3nexus.oauth2.infrastructure.security.session.DeviceKeyCookieProperties.class})
public class SessionLifetimeConfig {

    @Bean
    public SessionLifetimePolicy sessionLifetimePolicy(SessionLifetimeProperties properties) {
        return new SessionLifetimePolicy(properties.userLifetimes(), properties.internalLifetimes());
    }
}
