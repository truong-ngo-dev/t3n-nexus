package vn.t3nexus.oauth2.domain.login_session;

import org.junit.jupiter.api.Test;
import vn.t3nexus.oauth2.domain.login_session.SessionLifetimePolicy.Lifetimes;
import vn.t3nexus.oauth2.domain.user_account.Role;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** KT-18 (phần miền): hạn token = nhỏ hơn của (bây giờ + TTL, hạn phiên); số theo `tech.md` T1. */
class SessionLifetimePolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-13T03:00:00Z");

    private static final Lifetimes USER     = new Lifetimes(Duration.ofMinutes(15), Duration.ofDays(7), Duration.ofDays(30));
    private static final Lifetimes INTERNAL = new Lifetimes(Duration.ofMinutes(15), Duration.ofMinutes(30), Duration.ofHours(10));

    private final SessionLifetimePolicy policy = new SessionLifetimePolicy(USER, INTERNAL);

    @Test
    void session_expiry_follows_the_role() {
        assertThat(policy.sessionExpiresAt(Role.USER, NOW)).isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThat(policy.sessionExpiresAt(Role.ADMIN, NOW)).isEqualTo(NOW.plus(Duration.ofHours(10)));
    }

    @Test
    void access_token_uses_its_ttl_while_the_session_is_far_from_expiry() {
        Instant sessionExpiry = policy.sessionExpiresAt(Role.USER, NOW);

        assertThat(policy.accessTokenExpiresAt(Role.USER, NOW, sessionExpiry)).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void access_token_is_cut_to_the_session_expiry_when_the_session_ends_sooner() {
        Instant sessionExpiry = NOW.plus(Duration.ofMinutes(5));

        assertThat(policy.accessTokenExpiresAt(Role.USER, NOW, sessionExpiry)).isEqualTo(sessionExpiry);
    }

    @Test
    void refresh_token_uses_its_ttl_while_the_session_is_far_from_expiry() {
        Instant sessionExpiry = policy.sessionExpiresAt(Role.USER, NOW);

        assertThat(policy.refreshTokenExpiresAt(Role.USER, NOW, sessionExpiry)).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(policy.refreshTokenExpiresAt(Role.ADMIN, NOW, NOW.plus(Duration.ofHours(10))))
                .isEqualTo(NOW.plus(Duration.ofMinutes(30)));
    }

    @Test
    void refresh_token_is_cut_to_the_session_expiry_when_the_session_ends_sooner() {
        Instant sessionExpiry = NOW.plus(Duration.ofDays(2));

        assertThat(policy.refreshTokenExpiresAt(Role.USER, NOW, sessionExpiry)).isEqualTo(sessionExpiry);
    }

    @Test
    void no_token_ever_outlives_the_session() {
        Instant sessionExpiry = NOW.plusSeconds(1);

        assertThat(policy.accessTokenExpiresAt(Role.USER, NOW, sessionExpiry)).isBeforeOrEqualTo(sessionExpiry);
        assertThat(policy.refreshTokenExpiresAt(Role.USER, NOW, sessionExpiry)).isBeforeOrEqualTo(sessionExpiry);
    }

    @Test
    void refresh_ttl_shorter_than_twice_the_access_ttl_is_rejected() {
        Lifetimes bad = new Lifetimes(Duration.ofMinutes(15), Duration.ofMinutes(20), Duration.ofDays(30));

        assertThatThrownBy(() -> new SessionLifetimePolicy(bad, INTERNAL)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void session_ttl_not_longer_than_the_refresh_ttl_is_rejected() {
        Lifetimes bad = new Lifetimes(Duration.ofMinutes(15), Duration.ofDays(7), Duration.ofDays(7));

        assertThatThrownBy(() -> new SessionLifetimePolicy(USER, bad)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void the_dev_profile_numbers_satisfy_both_constraints() {
        Lifetimes dev = new Lifetimes(Duration.ofMinutes(1), Duration.ofMinutes(3), Duration.ofMinutes(10));

        assertThat(new SessionLifetimePolicy(dev, dev).sessionExpiresAt(Role.USER, NOW))
                .isEqualTo(NOW.plus(Duration.ofMinutes(10)));
    }
}
