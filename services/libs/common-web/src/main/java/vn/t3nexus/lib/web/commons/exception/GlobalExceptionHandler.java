package vn.t3nexus.lib.web.commons.exception;

import jakarta.servlet.http.HttpServletResponse;
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
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ApiResponse<?> handleResponseStatus(ResponseStatusException ex,
            HttpServletResponse response) {
        response.setStatus(ex.getStatusCode().value());
        return ApiResponse.error(ex.getReason() != null ? ex.getReason() : ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<?> handleGeneric(Exception ex) {
        return ApiResponse.error("An unexpected error occurred");
    }
}
