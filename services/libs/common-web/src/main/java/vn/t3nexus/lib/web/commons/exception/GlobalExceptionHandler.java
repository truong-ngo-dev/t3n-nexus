package vn.t3nexus.lib.web.commons.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.web.commons.response.ApiResponse;

import java.util.List;

/**
 * Centralized exception handler for Spring MVC controllers.
 * <br>Translates exceptions into a consistent {@link ApiResponse} format with a stable error {@code code}, and is the single place that logs
 * errors raised through HTTP (see {@code docs/global/4.convention/logging.md}): expected client/business errors (4xx) at INFO,
 * system errors (5xx) at ERROR with the stack trace. The error code is also set as a request attribute so the request completion line carries it.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Request attribute carrying the error code to the request completion line. Dies with the request, so it cannot leak to the next one. */
    public static final String ERROR_CODE_KEY = "error.code";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<?> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request, HttpServletResponse response) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        tag(request, "VALIDATION_FAILED");
        log.info("[GlobalExceptionHandler] VALIDATION_FAILED (400): {}", errors);
        return ApiResponse.validationError(errors);
    }

    /** Body that cannot be read (malformed JSON, wrong types): a client error, never a 500. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<?> handleMalformed(HttpMessageNotReadableException ex, HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        tag(request, "MALFORMED_REQUEST");
        // Không đưa chi tiết bộ phân tích ra client; chi tiết nằm ở log (mức INFO, không stack trace)
        log.info("[GlobalExceptionHandler] MALFORMED_REQUEST (400): {}", rootMessage(ex));
        return ApiResponse.error("Nội dung yêu cầu không đúng định dạng", "MALFORMED_REQUEST");
    }

    @ExceptionHandler(DomainException.class)
    public ApiResponse<?> handleDomain(DomainException ex, HttpServletRequest request, HttpServletResponse response) {
        int status = ex.getErrorCode().httpStatus();
        String code = codeOf(ex);
        response.setStatus(status);
        tag(request, code);
        if (status >= 500) {
            log.error("[GlobalExceptionHandler] {} ({}): {}", code, status, ex.getMessage(), ex);
        } else {
            log.info("[GlobalExceptionHandler] {} ({}): {}", code, status, ex.getMessage());
        }
        return ApiResponse.error(ex.getMessage(), code);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ApiResponse<?> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request, HttpServletResponse response) {
        return fromHttpStatus(ex.getStatusCode().value(), ex.getReason() != null ? ex.getReason() : ex.getMessage(), ex, request, response);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<?> handleGeneric(Exception ex, HttpServletRequest request, HttpServletResponse response) {
        // Lỗi framework đã mang sẵn mã trạng thái (405, 415, 404, thiếu tham số...): đó là lỗi do client, không phải 500.
        if (ex instanceof ErrorResponse errorResponse) {
            int status = errorResponse.getStatusCode().value();
            return fromHttpStatus(status, messageFor(status), ex, request, response);
        }
        // Client chỉ thấy thông điệp chung (không lộ chi tiết nội bộ), server luôn có stack trace để debug.
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        tag(request, "INTERNAL_ERROR");
        log.error("[GlobalExceptionHandler] Unhandled exception", ex);
        return ApiResponse.error("An unexpected error occurred", "INTERNAL_ERROR");
    }

    private ApiResponse<?> fromHttpStatus(int status, String message, Exception ex, HttpServletRequest request, HttpServletResponse response) {
        String code = "HTTP_" + status;
        response.setStatus(status);
        tag(request, code);
        if (status >= 500) {
            log.error("[GlobalExceptionHandler] {} ({}): {}", code, status, message, ex);
        } else {
            log.info("[GlobalExceptionHandler] {} ({}): {}", code, status, message);
        }
        return ApiResponse.error(message, code);
    }

    /** Ghi mã lỗi vào thuộc tính của yêu cầu để dòng hoàn tất của yêu cầu mang mã; thuộc tính hết cùng yêu cầu nên không rò sang yêu cầu sau. */
    private static void tag(HttpServletRequest request, String code) {
        request.setAttribute(ERROR_CODE_KEY, code);
    }

    /** Mã lỗi chữ ổn định: tên hằng của enum mã lỗi (ví dụ EMAIL_TAKEN); mã lỗi không phải enum thì dùng code() của nó. */
    private static String codeOf(DomainException ex) {
        return ex.getErrorCode() instanceof Enum<?> named ? named.name() : ex.getErrorCode().code();
    }

    private static String messageFor(int status) {
        return switch (status) {
            case 400 -> "Yêu cầu không hợp lệ";
            case 404 -> "Không tìm thấy";
            case 405 -> "Phương thức không được hỗ trợ";
            case 406 -> "Định dạng phản hồi không được hỗ trợ";
            case 415 -> "Kiểu nội dung không được hỗ trợ";
            default -> status >= 500 ? "An unexpected error occurred" : "Yêu cầu không thể xử lý";
        };
    }

    private static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
