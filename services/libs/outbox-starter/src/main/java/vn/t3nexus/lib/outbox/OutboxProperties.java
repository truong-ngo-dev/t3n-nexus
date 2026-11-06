package vn.t3nexus.lib.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình cổng ghi hộp thư.
 * <br>{@code app.outbox.sensitive-field-policy}: {@code strict} (mặc định: bí mật và dữ liệu định danh bị cấm trong
 * nội dung event), {@code secrets-only} (chỉ cấm bí mật; cho BC có event buộc mang email), {@code off} (không kiểm; chỉ
 * cho BC có event buộc mang bí mật, mỗi nơi dùng ghi lý do ở chính tệp cấu hình).
 */
@ConfigurationProperties("app.outbox")
public class OutboxProperties {

    public enum SensitiveFieldPolicy {
        STRICT, SECRETS_ONLY, OFF
    }

    private SensitiveFieldPolicy sensitiveFieldPolicy = SensitiveFieldPolicy.STRICT;

    public SensitiveFieldPolicy getSensitiveFieldPolicy() {
        return sensitiveFieldPolicy;
    }

    public void setSensitiveFieldPolicy(SensitiveFieldPolicy sensitiveFieldPolicy) {
        this.sensitiveFieldPolicy = sensitiveFieldPolicy;
    }
}
