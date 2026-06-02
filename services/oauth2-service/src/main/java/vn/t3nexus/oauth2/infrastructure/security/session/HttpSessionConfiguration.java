package vn.t3nexus.oauth2.infrastructure.security.session;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;

/**
 * Cấu hình Spring Session sử dụng JDBC (Database) để lưu trữ.
 * Giúp quản lý phiên đăng nhập tập trung và hỗ trợ việc thu hồi phiên từ xa.
 */
@Configuration
@EnableJdbcHttpSession
public class HttpSessionConfiguration {
    // Các tùy chỉnh bổ sung cho Session JDBC có thể định nghĩa ở đây nếu cần
}
