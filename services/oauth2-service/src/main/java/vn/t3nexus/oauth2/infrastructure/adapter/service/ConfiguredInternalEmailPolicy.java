package vn.t3nexus.oauth2.infrastructure.adapter.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.t3nexus.oauth2.domain.user_account.Email;
import vn.t3nexus.oauth2.domain.user_account.InternalEmailPolicy;

import java.util.List;
import java.util.Locale;

/**
 * Miền nội bộ đọc từ cấu hình {@code app.internal-email-domains} (phân cách bằng dấu phẩy).
 * So khớp đúng miền hoặc miền con ({@code a@t3nexus.com.vn}, {@code a@hr.t3nexus.com.vn}); không so khớp chuỗi con
 * ({@code a@not-t3nexus.com.vn} không bị chặn).
 */
@Component
public class ConfiguredInternalEmailPolicy implements InternalEmailPolicy {

    private final List<String> internalDomains;

    public ConfiguredInternalEmailPolicy(@Value("${app.internal-email-domains:t3nexus.com.vn}") List<String> internalDomains) {
        this.internalDomains = internalDomains.stream()
                .map(d -> d.trim().toLowerCase(Locale.ROOT))
                .filter(d -> !d.isEmpty())
                .toList();
    }

    @Override
    public boolean isInternal(Email email) {
        String domain = email.domain();
        return internalDomains.stream().anyMatch(d -> domain.equals(d) || domain.endsWith("." + d));
    }
}
