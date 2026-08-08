package vn.t3nexus.identity.presentation.me;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.t3nexus.identity.infrastructure.adapter.storage.avatar.AvatarStorageException;
import vn.t3nexus.lib.web.commons.response.ApiResponse;

// Cố ý KHÔNG cho AvatarStorageException extend DomainException — MinIO unavailable không phải
// business rule violation (đúng invariant domain vẫn giữ nguyên), mà là infra availability concern,
// cùng nhóm lý do với RateLimitExceededException (rate-limiter-starter) không extend DomainException.
// GlobalExceptionHandler dùng chung không biết về exception riêng của feature avatar này — handler
// cục bộ ở đây, khớp đúng contract 503 đã document (design.md § Error Cases).
@Slf4j
@RestControllerAdvice
public class AvatarStorageExceptionHandler {

    @ExceptionHandler(AvatarStorageException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiResponse<?> handleAvatarStorageException(AvatarStorageException ex) {
        log.error("[AvatarStorageExceptionHandler] MinIO unavailable", ex);
        return ApiResponse.error("Avatar storage is temporarily unavailable, please try again later");
    }
}
