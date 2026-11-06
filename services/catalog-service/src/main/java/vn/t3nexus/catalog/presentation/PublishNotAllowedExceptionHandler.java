package vn.t3nexus.catalog.presentation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.t3nexus.catalog.application.product.PublishNotAllowedException;
import vn.t3nexus.catalog.domain.product.MissingPublishCondition;
import vn.t3nexus.lib.web.commons.exception.GlobalExceptionHandler;
import vn.t3nexus.lib.web.commons.response.ApiResponse;

import java.util.List;

/**
 * Xử lý lỗi khi sản phẩm không đủ điều kiện để đăng (CAP-CAT-09).
 * Trả về HTTP 409 cùng danh sách các điều kiện còn thiếu trong errors.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class PublishNotAllowedExceptionHandler {

    private static final String CODE = "PRODUCT_NOT_PUBLISHABLE";

    @ExceptionHandler(PublishNotAllowedException.class)
    public ApiResponse<?> handlePublishNotAllowed(PublishNotAllowedException ex, HttpServletRequest request,
                                                  HttpServletResponse response) {
        response.setStatus(409);
        request.setAttribute(GlobalExceptionHandler.ERROR_CODE_KEY, CODE);
        log.info("[PublishNotAllowedExceptionHandler] {} (409)", CODE);

        List<String> errors = ex.missing().stream()
                .map(cond -> cond.templateId() != null
                        ? cond.code().name() + ":" + cond.templateId().getValue()
                        : cond.code().name())
                .toList();

        return new ApiResponse<>(false, null, ex.getMessage(), errors, CODE, MDC.get("traceId"));
    }
}
