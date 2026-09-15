package vn.t3nexus.scheduler.infrastructure.crosscutting.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Toàn bộ REST API của scheduler-service nằm dưới {@code /api/admin/scheduled-jobs/**} — Admin-only,
 * KHÔNG có endpoint public nào (khác `catalog-service`, không cần danh sách {@code permitAll}, đơn giản
 * hơn: {@code anyRequest().authenticated()} là đủ). CSRF tắt vì đây là API stateless JWT bearer, không
 * phải form-based session — cùng lý do các service khác trong hệ đã tắt.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain httpSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authReqs -> authReqs.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
