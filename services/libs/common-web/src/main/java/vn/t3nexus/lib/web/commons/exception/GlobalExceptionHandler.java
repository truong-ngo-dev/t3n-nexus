package vn.t3nexus.lib.web.commons.exception;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.web.commons.response.ApiResponse;

import java.util.List;

/**
 * Centralized exception handler for Spring MVC controllers.
 * <br>Translates various exceptions into a consistent {@link ApiResponse} format.
 */
@Slf4j
@RestControllerAdvice
public class    GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<?> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();
        return ApiResponse.validationError(errors);
    }

    @ExceptionHandler(DomainException.class)
    public ApiResponse<?> handleDomain(DomainException ex, HttpServletResponse response) {
        response.setStatus(ex.getErrorCode().httpStatus());
        // warn (không error) — đây là business rule violation dự kiến (409/404/400...), không phải
        // bug. Vẫn cần log vì trước đây MỌI domain exception ở MỌI service dùng lib này im lặng
        // hoàn toàn — không cách nào biết qua log ai đang bị 409 email trùng, 404 token sai...
        log.warn("[GlobalExceptionHandler] {} ({}): {}", ex.getErrorCode(), ex.getErrorCode().httpStatus(), ex.getMessage());
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ApiResponse<?> handleResponseStatus(ResponseStatusException ex,
            HttpServletResponse response) {
        response.setStatus(ex.getStatusCode().value());
        log.warn("[GlobalExceptionHandler] ResponseStatusException {}: {}", ex.getStatusCode(), ex.getReason());
        return ApiResponse.error(ex.getReason() != null ? ex.getReason() : ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<?> handleGeneric(Exception ex) {
        // Trước đây không log gì — exception thật biến mất hoàn toàn, kể cả trong console dev.
        // Client chỉ nên thấy message chung chung (không rò rỉ chi tiết nội bộ), nhưng server
        // luôn phải có stack trace để debug.
        log.error("[GlobalExceptionHandler] Unhandled exception", ex);
        return ApiResponse.error("An unexpected error occurred");
    }
}
