package vn.t3nexus.oauth2.infrastructure.security.extension.authentication;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Giới hạn số lần thử đăng nhập (`tech.md` T3, nhóm 2 của `rate-limiting-layers.md`), nạp lúc khởi động:
 * <ul>
 *   <li>theo cặp (email, địa chỉ mạng): mức thấp, để gõ nhầm vài lần vẫn đăng nhập lại được và một địa chỉ không dò được
 *       mật khẩu của một email;</li>
 *   <li>riêng email: mức cao hơn, chặn dò phân tán từ nhiều địa chỉ.</li>
 * </ul>
 * Sai cấu hình (mức không dương, cửa sổ không dương, mức theo email thấp hơn mức theo cặp) thì dịch vụ không khởi động.
 */
@ConfigurationProperties(prefix = "app.login.rate-limit")
public record LoginRateLimitProperties(
        @DefaultValue("5") int pairAttempts,
        @DefaultValue("5m") Duration pairWindow,
        @DefaultValue("15") int emailAttempts,
        @DefaultValue("15m") Duration emailWindow) {

    public LoginRateLimitProperties {
        if (pairAttempts < 1 || emailAttempts < 1) {
            throw new IllegalArgumentException("login rate limit attempts must be at least 1");
        }
        if (pairWindow == null || pairWindow.isZero() || pairWindow.isNegative()
                || emailWindow == null || emailWindow.isZero() || emailWindow.isNegative()) {
            throw new IllegalArgumentException("login rate limit windows must be positive");
        }
        if (emailAttempts < pairAttempts) {
            throw new IllegalArgumentException("the per-email limit must not be lower than the per-(email, address) limit");
        }
    }
}
