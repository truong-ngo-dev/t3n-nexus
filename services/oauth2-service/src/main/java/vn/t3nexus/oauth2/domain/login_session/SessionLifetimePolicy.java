package vn.t3nexus.oauth2.domain.login_session;

import vn.t3nexus.oauth2.domain.user_account.Role;

import java.time.Duration;
import java.time.Instant;

/**
 * Chính sách hạn theo vai của tài khoản (`tech.md` T1): hạn phiên và TTL các loại token. Mọi token có hạn bằng nhỏ hơn
 * của (bây giờ + TTL của loại token, hạn phiên). Miền không đọc cấu hình: giá trị đưa vào lúc dựng.
 *
 * <p>Vai {@link Role#USER} dùng bộ TTL của người dùng; mọi vai khác (nội bộ) dùng bộ TTL nội bộ. Ràng buộc kiểm lúc dựng:
 * TTL refresh token ≥ 2 lần TTL access token, và hạn phiên > TTL refresh token (`analysis/login-session.md` mục 3.4).
 */
public final class SessionLifetimePolicy {

    /** TTL của một nhóm vai (người dùng, nội bộ), hoặc của profile dev. */
    public record Lifetimes(Duration accessTtl, Duration refreshTtl, Duration sessionTtl) {
    }

    private final Lifetimes userLifetimes;
    private final Lifetimes internalLifetimes;

    public SessionLifetimePolicy(Lifetimes userLifetimes, Lifetimes internalLifetimes) {
        this.userLifetimes     = validated("user", userLifetimes);
        this.internalLifetimes = validated("internal", internalLifetimes);
    }

    /** Hạn phiên người dùng, đặt lúc mở và không bao giờ kéo dài. */
    public Instant sessionExpiresAt(Role role, Instant now) {
        return now.plus(lifetimesOf(role).sessionTtl());
    }

    /** Hạn access token (và ID token): nhỏ hơn của (now + TTL access, hạn phiên). */
    public Instant accessTokenExpiresAt(Role role, Instant now, Instant sessionExpiresAt) {
        return earlier(now.plus(lifetimesOf(role).accessTtl()), sessionExpiresAt);
    }

    /** Hạn refresh token: nhỏ hơn của (now + TTL refresh, hạn phiên). */
    public Instant refreshTokenExpiresAt(Role role, Instant now, Instant sessionExpiresAt) {
        return earlier(now.plus(lifetimesOf(role).refreshTtl()), sessionExpiresAt);
    }

    private Lifetimes lifetimesOf(Role role) {
        return role == Role.USER ? userLifetimes : internalLifetimes;
    }

    private static Instant earlier(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private static Lifetimes validated(String name, Lifetimes lifetimes) {
        if (lifetimes == null || lifetimes.accessTtl() == null || lifetimes.refreshTtl() == null
                || lifetimes.sessionTtl() == null) {
            throw new IllegalArgumentException("Lifetimes '" + name + "' require access, refresh and session TTL");
        }
        if (lifetimes.accessTtl().isZero() || lifetimes.accessTtl().isNegative()) {
            throw new IllegalArgumentException("Access TTL of '" + name + "' must be positive");
        }
        if (lifetimes.refreshTtl().compareTo(lifetimes.accessTtl().multipliedBy(2)) < 0) {
            throw new IllegalArgumentException("Refresh TTL of '" + name + "' must be at least twice the access TTL");
        }
        if (lifetimes.sessionTtl().compareTo(lifetimes.refreshTtl()) <= 0) {
            throw new IllegalArgumentException("Session TTL of '" + name + "' must be longer than the refresh TTL");
        }
        return lifetimes;
    }
}
