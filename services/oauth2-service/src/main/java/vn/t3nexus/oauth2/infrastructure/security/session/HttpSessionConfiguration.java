package vn.t3nexus.oauth2.infrastructure.security.session;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;

@Configuration
@EnableJdbcHttpSession
public class HttpSessionConfiguration {
}
