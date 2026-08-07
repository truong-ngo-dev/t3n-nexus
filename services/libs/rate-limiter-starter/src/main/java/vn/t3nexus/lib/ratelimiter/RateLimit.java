package vn.t3nexus.lib.ratelimiter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đặt trên method (thường là {@code CommandHandler.handle()}) để enforce rate-limit khai báo,
 * thay cho gọi tay {@link RateLimiter#tryAcquire}. Xử lý bởi {@link RateLimitAspect}.
 *
 * <p>{@code key} là SpEL expression, evaluate với tham số method làm variable — VD với
 * {@code handle(Command command)}:
 * <pre>{@code
 * @RateLimit(key = "'register:' + #command.clientIp()", limit = 5, windowSeconds = 3600)
 * public Result handle(Command command) { ... }
 * }</pre>
 *
 * Vượt limit ném {@link RateLimitExceededException} — map sẵn 429 qua {@link RateLimitExceptionHandler},
 * không cần mỗi service tự định nghĩa exception/ErrorCode riêng cho rate-limit.
 *
 * <p>Aspect chạy trước {@code @Transactional} (xem {@link RateLimitAspect#getOrder()}) — request bị
 * từ chối không mở transaction/tốn connection.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RateLimit {

    /** SpEL expression tính key Redis — nên bắt đầu bằng prefix riêng (VD "register:") để tránh đụng namespace. */
    String key();

    /** Số lần cho phép trong 1 window. */
    int limit();

    /** Độ dài window, tính bằng giây. */
    long windowSeconds();

    /** Message trả về khi vượt limit — string tĩnh, không phải SpEL. */
    String message() default "Too many requests, please try again later";
}
