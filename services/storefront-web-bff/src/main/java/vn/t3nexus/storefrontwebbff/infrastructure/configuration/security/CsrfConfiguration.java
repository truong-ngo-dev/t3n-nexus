package vn.t3nexus.storefrontwebbff.infrastructure.configuration.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRepository;

/**
 * Kho mã CSRF của BFF (`tech.md` T4): cookie {@code XSRF-TOKEN} <b>không {@code HttpOnly}</b> (JavaScript của SPA phải đọc để gửi lại trong
 * {@code X-XSRF-TOKEN}), {@code Path=/}, {@code SameSite=Lax}, không {@code Max-Age} (cookie phiên của trình duyệt, cùng loại với cookie phiên
 * BFF nên không cần gia hạn trượt, ADR-0005 A). {@code Secure} do Spring đặt theo lược đồ của yêu cầu (đã đúng nhờ {@code X-Forwarded-Proto}
 * của cổng). Tách khỏi {@link SecurityConfiguration} vì bộ xử lý đăng nhập thành công cũng cần kho này để xoay mã.
 */
@Configuration
public class CsrfConfiguration {

    @Bean
    public ServerCsrfTokenRepository csrfTokenRepository() {
        CookieServerCsrfTokenRepository repository = CookieServerCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Lax"));
        return repository;
    }
}
