package vn.t3nexus.oauth2.infrastructure.security.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Thuộc tính của cookie mã thiết bị {@code DEVICE_KEY} (ADR-0005, `tech.md` T5): hạn 12 tháng, gia hạn mỗi lần đăng nhập;
 * cờ {@code Secure} tắt được ở profile dev vì chạy HTTP cục bộ.
 */
@ConfigurationProperties(prefix = "app.device-key")
public record DeviceKeyCookieProperties(Duration maxAge, boolean secure) {

    public DeviceKeyCookieProperties {
        if (maxAge == null || maxAge.isZero() || maxAge.isNegative()) {
            throw new IllegalArgumentException("app.device-key.max-age must be positive");
        }
    }
}
