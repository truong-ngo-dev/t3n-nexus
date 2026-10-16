package vn.t3nexus.oauth2.infrastructure.security.extension.authentication;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import vn.t3nexus.lib.ratelimiter.RateLimiter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Giới hạn số lần thử đăng nhập (bước 10, KT-15, 16, 17): hai khóa (email + địa chỉ mạng, và riêng email), đếm mọi lần thử, vượt thì
 * chuyển tới trang 429 và không tới bước kiểm mật khẩu; bộ đếm lỗi thì mở cửa.
 */
class LoginRateLimitFilterTest {

    private static final LoginRateLimitProperties LIMITS =
            new LoginRateLimitProperties(5, Duration.ofMinutes(5), 15, Duration.ofMinutes(15));

    /** Bộ đếm trong bộ nhớ: mỗi khóa cho qua tới {@code limit} lần rồi từ chối; từ chối thì không ghi thêm (như bộ đếm Redis thật). */
    private static final class InMemoryLimiter implements RateLimiter {
        final Map<String, Integer> counts = new HashMap<>();
        final List<String> keys = new ArrayList<>();

        @Override
        public boolean tryAcquire(String key, int limit, Duration window) {
            keys.add(key);
            int used = counts.getOrDefault(key, 0);
            if (used >= limit) return false;
            counts.put(key, used + 1);
            return true;
        }

        long countOfKeysStartingWith(String prefix) {
            return counts.entrySet().stream().filter(e -> e.getKey().startsWith(prefix)).mapToInt(Map.Entry::getValue).sum();
        }
    }

    private record Outcome(boolean passedOn, String forwardedTo, Object retryAfter) {}

    private static Outcome attempt(RateLimiter limiter, String method, String path, String username, String address) throws Exception {
        LoginRateLimitFilter filter = new LoginRateLimitFilter(limiter, LIMITS);
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        if (username != null) request.setParameter("username", username);
        if (address != null) request.addHeader("X-Forwarded-For", address);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return new Outcome(chain.getRequest() != null, response.getForwardedUrl(),
                request.getAttribute(LoginRateLimitFilter.RETRY_AFTER_SECONDS));
    }

    private static Outcome login(RateLimiter limiter, String username, String address) throws Exception {
        return attempt(limiter, "POST", "/login", username, address);
    }

    // ───────────── KT-15: theo cặp (email, địa chỉ mạng) ─────────────

    @Test
    void the_sixth_attempt_for_one_email_from_one_address_is_throttled_without_reaching_the_password_check() throws Exception {
        InMemoryLimiter limiter = new InMemoryLimiter();
        for (int i = 0; i < 5; i++) assertThat(login(limiter, "nguyen@example.com", "203.0.113.7").passedOn()).isTrue();

        Outcome sixth = login(limiter, "nguyen@example.com", "203.0.113.7");

        assertThat(sixth.passedOn()).isFalse();
        assertThat(sixth.forwardedTo()).isEqualTo(LoginRateLimitFilter.THROTTLED_PATH);
        assertThat(sixth.retryAfter()).isEqualTo(300L);
    }

    @Test
    void an_email_that_does_not_exist_is_treated_exactly_like_one_that_does() throws Exception {
        InMemoryLimiter limiter = new InMemoryLimiter();
        for (String email : List.of("nguyen@example.com", "khong-co@example.com")) {
            for (int i = 0; i < 5; i++) assertThat(login(limiter, email, "203.0.113.7").passedOn()).isTrue();
            assertThat(login(limiter, email, "203.0.113.7").forwardedTo()).isEqualTo(LoginRateLimitFilter.THROTTLED_PATH);
        }
    }

    @Test
    void the_same_email_written_differently_counts_as_one() throws Exception {
        InMemoryLimiter limiter = new InMemoryLimiter();
        for (int i = 0; i < 3; i++) login(limiter, "Nguyen@Example.COM", "203.0.113.7");
        for (int i = 0; i < 2; i++) login(limiter, "  nguyen@example.com ", "203.0.113.7");

        assertThat(login(limiter, "nguyen@example.com", "203.0.113.7").passedOn()).isFalse();
    }

    @Test
    void a_malformed_email_is_counted_too_because_every_attempt_still_costs_a_password_hash() throws Exception {
        InMemoryLimiter limiter = new InMemoryLimiter();
        for (int i = 0; i < 5; i++) assertThat(login(limiter, "khong-phai-email", "203.0.113.7").passedOn()).isTrue();

        assertThat(login(limiter, "khong-phai-email", "203.0.113.7").passedOn()).isFalse();
    }

    @Test
    void another_address_is_not_blocked_by_the_pair_limit_of_the_first() throws Exception {
        InMemoryLimiter limiter = new InMemoryLimiter();
        for (int i = 0; i < 6; i++) login(limiter, "nguyen@example.com", "203.0.113.7");

        assertThat(login(limiter, "nguyen@example.com", "198.51.100.9").passedOn()).isTrue();
    }

    // ───────────── KT-16: riêng email, từ nhiều địa chỉ ─────────────

    @Test
    void many_addresses_trying_one_email_hit_the_email_limit_even_though_none_hit_the_pair_limit() throws Exception {
        InMemoryLimiter limiter = new InMemoryLimiter();
        for (int i = 1; i <= 15; i++) {
            assertThat(login(limiter, "nguyen@example.com", "198.51.100." + i).passedOn()).as("attempt " + i).isTrue();
        }

        Outcome sixteenth = login(limiter, "nguyen@example.com", "198.51.100.200");

        assertThat(sixteenth.passedOn()).isFalse();
        assertThat(sixteenth.forwardedTo()).isEqualTo(LoginRateLimitFilter.THROTTLED_PATH);
        assertThat(sixteenth.retryAfter()).isEqualTo(900L);
        assertThat(login(limiter, "khac@example.com", "198.51.100.200").passedOn()).isTrue();   // email khác không bị ảnh hưởng
    }

    @Test
    void attempts_blocked_at_the_pair_level_do_not_use_up_the_email_budget() throws Exception {
        InMemoryLimiter limiter = new InMemoryLimiter();
        for (int i = 0; i < 20; i++) login(limiter, "nguyen@example.com", "203.0.113.7");   // 5 qua, 15 bị chặn ở mức cặp

        assertThat(limiter.countOfKeysStartingWith("login_email:")).isEqualTo(5);
        assertThat(login(limiter, "nguyen@example.com", "198.51.100.9").passedOn()).isTrue();   // người dùng thật ở địa chỉ khác vẫn vào được
    }

    // ───────────── KT-17: bộ đếm lỗi thì mở cửa ─────────────

    @Test
    void when_the_counter_is_unavailable_the_attempt_goes_through() throws Exception {
        RateLimiter broken = (key, limit, window) -> { throw new IllegalStateException("redis is down"); };

        Outcome outcome = login(broken, "nguyen@example.com", "203.0.113.7");

        assertThat(outcome.passedOn()).isTrue();
        assertThat(outcome.forwardedTo()).isNull();
    }

    // ───────────── phạm vi và riêng tư ─────────────

    @Test
    void only_the_login_post_is_counted() throws Exception {
        InMemoryLimiter limiter = new InMemoryLimiter();
        attempt(limiter, "GET", "/login", "nguyen@example.com", "203.0.113.7");
        attempt(limiter, "POST", "/register", "nguyen@example.com", "203.0.113.7");

        assertThat(limiter.keys).isEmpty();
    }

    @Test
    void the_counter_keys_contain_neither_the_email_nor_the_address() throws Exception {
        InMemoryLimiter limiter = new InMemoryLimiter();
        login(limiter, "nguyen@example.com", "203.0.113.7");

        assertThat(limiter.keys).isNotEmpty().allSatisfy(key ->
                assertThat(key).doesNotContain("nguyen").doesNotContain("example").doesNotContain("203.0.113.7"));
    }

    // ───────────── cấu hình ─────────────

    @Test
    void a_misconfigured_limit_is_refused_at_startup() {
        assertThatThrownBy(() -> new LoginRateLimitProperties(0, Duration.ofMinutes(5), 15, Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginRateLimitProperties(5, Duration.ZERO, 15, Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginRateLimitProperties(10, Duration.ofMinutes(5), 5, Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);   // mức theo email thấp hơn mức theo cặp
    }
}
