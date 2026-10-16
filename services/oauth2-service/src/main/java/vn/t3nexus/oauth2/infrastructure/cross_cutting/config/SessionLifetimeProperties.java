package vn.t3nexus.oauth2.infrastructure.cross_cutting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import vn.t3nexus.oauth2.domain.login_session.SessionLifetimePolicy.Lifetimes;

import java.time.Duration;

/**
 * Hạn phiên và TTL token theo vai (`tech.md` T1), nạp lúc khởi động; {@link vn.t3nexus.oauth2.domain.login_session.SessionLifetimePolicy}
 * từ chối cấu hình vi phạm ràng buộc (refresh ≥ 2 × access, hạn phiên > refresh) nên ứng dụng không khởi động được. Bộ số dev
 * (profile {@code dev}) nằm ở {@code application-dev.properties}.
 */
@ConfigurationProperties(prefix = "app.session")
public record SessionLifetimeProperties(Group user, Group internal) {

    public record Group(Duration accessTtl, Duration refreshTtl, Duration sessionTtl) {
        Lifetimes toLifetimes() {
            return new Lifetimes(accessTtl, refreshTtl, sessionTtl);
        }
    }

    public Lifetimes userLifetimes()     { return user.toLifetimes(); }
    public Lifetimes internalLifetimes() { return internal.toLifetimes(); }
}
