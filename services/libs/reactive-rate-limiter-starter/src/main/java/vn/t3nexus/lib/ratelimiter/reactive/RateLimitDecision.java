package vn.t3nexus.lib.ratelimiter.reactive;

import java.time.Duration;

/**
 * Kết quả một lần xin lượt: cho qua, hoặc từ chối kèm thời gian còn lại đến khi giải phóng một lượt (lượt cũ nhất rời cửa sổ).
 * Thời gian này là giá trị đúng cho {@code Retry-After}, khác độ dài cả cửa sổ.
 *
 * @param allowed    yêu cầu được cho qua
 * @param retryAfter {@link Duration#ZERO} khi cho qua; khi từ chối là thời gian còn lại đến khi giải phóng một lượt
 */
public record RateLimitDecision(boolean allowed, Duration retryAfter) {

    public static RateLimitDecision allow() {
        return new RateLimitDecision(true, Duration.ZERO);
    }

    public static RateLimitDecision deny(Duration retryAfter) {
        return new RateLimitDecision(false, retryAfter);
    }

    /** Giá trị {@code Retry-After} tính bằng giây: làm tròn lên, tối thiểu 1 khi từ chối, 0 khi cho qua. */
    public long retryAfterSeconds() {
        if (allowed) return 0;
        return Math.max(1, (retryAfter.toMillis() + 999) / 1000);
    }
}
