package vn.t3nexus.webgateway.infrastructure.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.server.EnableRedisWebSession;
import org.springframework.web.server.session.CookieWebSessionIdResolver;
import org.springframework.web.server.session.WebSessionIdResolver;

@Configuration
@EnableRedisWebSession
public class RedisConfiguration {

    /**
     * Distinct cookie name prevents the browser from conflating this service's session
     * with the oauth2-service SESSION cookie — both run on localhost and browsers share
     * cookie scope across ports, which would overwrite the PKCE verifier on callback.
     */
    @Bean
    public WebSessionIdResolver webSessionIdResolver() {
        CookieWebSessionIdResolver resolver = new CookieWebSessionIdResolver();
        resolver.setCookieName("WEBGW_SESSION");
        return resolver;
    }
}
