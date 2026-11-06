package vn.t3nexus.audit.presentation.auditentry;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import vn.t3nexus.lib.web.commons.exception.GlobalExceptionHandler;
import vn.t3nexus.lib.web.commons.response.ApiResponse;

/**
 * Xử lý ngoại lệ tham số yêu cầu cho AuditEntryController.
 * <br>Bắt lỗi sai kiểu và thiếu tham số thành HTTP 400 và không làm lộ giá trị đầu vào.
 */
// Ưu tiên cao nhất: GlobalExceptionHandler có @ExceptionHandler(Exception.class) nên nếu nó đứng trước, lỗi kiểu tham số sẽ thành 500
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class AuditRequestExceptionHandler {

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResponse<?> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        request.setAttribute(GlobalExceptionHandler.ERROR_CODE_KEY, "AUDIT_PARAMETER_INVALID");
        String paramName = ex.getName();
        String message = "Tham số '" + paramName + "' không đúng định dạng";
        return ApiResponse.error(message, "AUDIT_PARAMETER_INVALID");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResponse<?> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        request.setAttribute(GlobalExceptionHandler.ERROR_CODE_KEY, "AUDIT_PARAMETER_INVALID");
        String paramName = ex.getParameterName();
        String message = "Thiếu tham số bắt buộc '" + paramName + "'";
        return ApiResponse.error(message, "AUDIT_PARAMETER_INVALID");
    }
}
