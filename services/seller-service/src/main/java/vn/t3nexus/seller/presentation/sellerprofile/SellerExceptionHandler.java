package vn.t3nexus.seller.presentation.sellerprofile;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.web.commons.exception.GlobalExceptionHandler;
import vn.t3nexus.lib.web.commons.response.ApiResponse;
import vn.t3nexus.seller.domain.sellerprofile.SellerErrorCode;
import vn.t3nexus.seller.domain.sellerprofile.SellerFieldException;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bộ xử lý ngoại lệ tập trung cho các điểm cuối của SellerProfileController.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class SellerExceptionHandler {

    private static final Pattern CONSTRAINT_PATTERN = Pattern.compile("constraint \"([^\"]+)\"");

    private static final Set<String> CONFLICT_CODES = Set.of(
            SellerErrorCode.SELLER_PROFILE_EXISTS.code(),
            SellerErrorCode.SHOP_NAME_TAKEN.code(),
            SellerErrorCode.IDENTITY_ALREADY_REGISTERED.code(),
            SellerErrorCode.CONCURRENT_UPDATE.code(),
            SellerErrorCode.STALE_VERSION.code()
    );

    private final MeterRegistry meterRegistry;

    public SellerExceptionHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
    }

    @ExceptionHandler(DomainException.class)
    public ApiResponse<?> handleDomain(
            DomainException ex,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        int status = ex.getErrorCode().httpStatus();
        String code = ex.getErrorCode() instanceof Enum<?> e ? e.name() : ex.getErrorCode().code();

        response.setStatus(status);
        request.setAttribute(GlobalExceptionHandler.ERROR_CODE_KEY, code);

        log.info("SELLER_REJECTED code={} status={}", code, status);

        if (CONFLICT_CODES.contains(code)) {
            log.info("SELLER_CONFLICT code={}", code);
            meterRegistry.counter("seller.profile.conflict", "code", code).increment();
        }

        // Lỗi trường: nêu tên trường ở errors để giao diện gắn vào đúng ô (không phải cắt từ message).
        if (ex instanceof SellerFieldException fieldException) {
            return ApiResponse.error(ex.getMessage(), code, List.of(fieldException.field()));
        }

        return ApiResponse.error(ex.getMessage(), code);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ApiResponse<?> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String constraint = constraintName(ex);

        SellerErrorCode matchedErrorCode = null;
        if ("uq_seller_profiles_owner".equals(constraint)) {
            matchedErrorCode = SellerErrorCode.SELLER_PROFILE_EXISTS;
        } else if ("uq_seller_profiles_shop_name_key".equals(constraint)) {
            matchedErrorCode = SellerErrorCode.SHOP_NAME_TAKEN;
        } else if ("uq_seller_profiles_identity_key_effective".equals(constraint)) {
            matchedErrorCode = SellerErrorCode.IDENTITY_ALREADY_REGISTERED;
        }

        if (matchedErrorCode != null) {
            int status = matchedErrorCode.httpStatus();
            String code = matchedErrorCode.name();

            response.setStatus(status);
            request.setAttribute(GlobalExceptionHandler.ERROR_CODE_KEY, code);

            log.info("SELLER_REJECTED code={} status={}", code, status);
            log.info("SELLER_CONFLICT code={}", code);
            meterRegistry.counter("seller.profile.conflict", "code", code).increment();

            return ApiResponse.error(matchedErrorCode.defaultMessage(), code);
        }

        response.setStatus(500);
        request.setAttribute(GlobalExceptionHandler.ERROR_CODE_KEY, "INTERNAL_ERROR");

        log.error("SELLER_UNMAPPED_CONSTRAINT constraint={}", constraint != null ? constraint : "unknown");

        return ApiResponse.error("An unexpected error occurred", "INTERNAL_ERROR");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ApiResponse<?> handleOptimisticLockingFailure(
            OptimisticLockingFailureException ex,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        SellerErrorCode errorCode = SellerErrorCode.CONCURRENT_UPDATE;
        int status = errorCode.httpStatus();
        String code = errorCode.name();

        response.setStatus(status);
        request.setAttribute(GlobalExceptionHandler.ERROR_CODE_KEY, code);

        log.info("SELLER_REJECTED code={} status={}", code, status);
        log.info("SELLER_CONFLICT code={}", code);
        meterRegistry.counter("seller.profile.conflict", "code", code).increment();

        return ApiResponse.error(errorCode.defaultMessage(), code);
    }

    /**
     * Tham số đường dẫn hoặc truy vấn sai kiểu (ví dụ mã hồ sơ không phải UUID): 400, chỉ nêu tên tham số, không lặp lại giá trị.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResponse<?> handleArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        SellerErrorCode errorCode = SellerErrorCode.SELLER_FIELD_INVALID;
        String code = errorCode.name();

        response.setStatus(errorCode.httpStatus());
        request.setAttribute(GlobalExceptionHandler.ERROR_CODE_KEY, code);

        log.info("SELLER_REJECTED code={} status={}", code, errorCode.httpStatus());

        return ApiResponse.error("Trường không hợp lệ: " + ex.getName(), code);
    }

    private String constraintName(Throwable root) {
        Throwable current = root;
        Throwable deepest = root;
        while (current != null) {
            deepest = current;
            if (current instanceof org.hibernate.exception.ConstraintViolationException cve) {
                if (cve.getConstraintName() != null) {
                    return cve.getConstraintName();
                }
            }
            current = current.getCause();
        }

        if (deepest != null && deepest.getMessage() != null) {
            Matcher matcher = CONSTRAINT_PATTERN.matcher(deepest.getMessage());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return null;
    }
}
