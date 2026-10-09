package vn.t3nexus.catalog.infrastructure.crosscutting.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.t3nexus.lib.web.commons.response.ApiResponse;

/**
 * Xung đột do 2 request song song — không phải lỗi hệ thống. Use case đã kiểm trước (trùng tên/mã...), nhưng khi 2
 * request cùng lọt qua bước kiểm thì DB là lớp chặn cuối (unique index, FK RESTRICT, {@code @Version}). Không có handler
 * này, các exception đó rơi vào handler {@code Exception} chung của common-web và trả 500.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PersistenceConflictExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<?> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("[PersistenceConflictExceptionHandler] integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ApiResponse.error("Dữ liệu xung đột với bản ghi đã có, vui lòng tải lại và thử lại");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<?> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("[PersistenceConflictExceptionHandler] optimistic lock: {}", ex.getMessage());
        return ApiResponse.error("Dữ liệu đã bị thay đổi bởi người khác, vui lòng tải lại và thử lại");
    }
}
