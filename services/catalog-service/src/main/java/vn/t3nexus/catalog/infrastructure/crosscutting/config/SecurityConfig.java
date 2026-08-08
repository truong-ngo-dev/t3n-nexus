package vn.t3nexus.catalog.infrastructure.crosscutting.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

// Trước đây không có SecurityFilterChain nào — Spring Boot auto-config mặc định áp
// anyRequest().authenticated() cho toàn bộ endpoint, kể cả GET public (category tree,
// product detail, brand list) mà Guest phải xem được không cần login. Route qua web-gateway
// (RouteConfiguration) chỉ đúng khi tầng này permitAll đúng các endpoint public — nếu không,
// toàn bộ luồng browse của Guest/Customer sẽ 401 dù route đã đúng.
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain httpSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authReqs -> authReqs
                        .requestMatchers(new OrRequestMatcher(
                                PathPatternRequestMatcher.withDefaults().matcher("/api/categories"),
                                PathPatternRequestMatcher.withDefaults().matcher("/api/categories/*/attributes"),
                                PathPatternRequestMatcher.withDefaults().matcher("/api/brands"),
                                PathPatternRequestMatcher.withDefaults().matcher("/api/products/*"),
                                PathPatternRequestMatcher.withDefaults().matcher("/api/products/*/variants")))
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
