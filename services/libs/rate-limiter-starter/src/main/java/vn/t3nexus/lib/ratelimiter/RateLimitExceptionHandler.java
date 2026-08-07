package vn.t3nexus.lib.ratelimiter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.t3nexus.lib.web.commons.response.ApiResponse;

/**
 * Tự chứa trong lib — service dùng {@code rate-limiter-starter} tự động có 429 đúng chuẩn cho
 * {@link RateLimitExceededException}, không cần khai báo gì thêm, không cần {@code common-web}'s
 * {@code GlobalExceptionHandler} biết về rate-limit (tránh phụ thuộc ngược generic lib → feature lib).
 */
@Slf4j
@RestControllerAdvice
public class RateLimitExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ApiResponse<?> handleRateLimitExceeded(RateLimitExceededException ex) {
        // Message ở đây chỉ là text tĩnh từ @RateLimit(message=...), KHÔNG có key — key đã được
        // log riêng ở RateLimitAspect (nơi duy nhất có key đã evaluate). Log ở đây chỉ để xác nhận
        // response 429 thật sự được trả, không phải nguồn thông tin chính.
        log.warn("[RateLimitExceptionHandler] returning 429: {}", ex.getMessage());
        return ApiResponse.error(ex.getMessage());
    }
}
