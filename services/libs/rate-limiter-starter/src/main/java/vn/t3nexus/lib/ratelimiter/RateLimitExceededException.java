package vn.t3nexus.lib.ratelimiter;

/**
 * Ném khi {@link RateLimit} bị vượt. Cố ý KHÔNG extend {@code DomainException} — rate limit
 * không phải business invariant của domain model (đúng command y hệt gửi lại sau khi hết window
 * sẽ pass, domain state không đổi 1 chút nào), mà là cross-cutting infra concern, cùng nhóm với
 * {@code @Transactional}/{@code @Cacheable}. Luôn map 429 — không cần ErrorCode riêng, xem
 * {@link RateLimitExceptionHandler}.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
