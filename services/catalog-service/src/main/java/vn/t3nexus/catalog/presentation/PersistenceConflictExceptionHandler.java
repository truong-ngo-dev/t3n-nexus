package vn.t3nexus.catalog.presentation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.t3nexus.catalog.domain.CatalogErrorCode;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateErrorCode;
import vn.t3nexus.catalog.domain.brand.BrandErrorCode;
import vn.t3nexus.catalog.domain.category.CategoryErrorCode;
import vn.t3nexus.catalog.domain.variant.VariantErrorCode;
import vn.t3nexus.lib.common.domain.exception.ErrorCode;
import vn.t3nexus.lib.web.commons.exception.GlobalExceptionHandler;
import vn.t3nexus.lib.web.commons.response.ApiResponse;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Xung đột do hai yêu cầu song song, không phải lỗi hệ thống (ADR-0011). Use case đã kiểm sớm (tên trùng, còn con...),
 * nhưng khi hai yêu cầu cùng lọt qua bước kiểm thì cơ sở dữ liệu là chốt cuối: ràng buộc duy nhất, khóa ngoài
 * {@code RESTRICT} và {@code @Version}. Ở đây ánh xạ TÊN ràng buộc sang mã lỗi nghiệp vụ (409). Ràng buộc không có trong
 * bảng thì là lỗi hệ thống 500, để lộ chứ không đoán (data.md mục 8).
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class PersistenceConflictExceptionHandler {

    private static final String FK_CATEGORY_PARENT = "fk_category_parent";

    private static final Pattern CONSTRAINT_PATTERN = Pattern.compile("constraint \"([^\"]+)\"");

    /** Tên ràng buộc (hoặc chỉ mục duy nhất) trong {@code V1}, {@code V2} sang mã lỗi; giữ đồng bộ với {@code data.md} mục 8. */
    private static final Map<String, ErrorCode> CONSTRAINT_ERRORS = Map.of(
            "uq_category_parent_name_key", CategoryErrorCode.CATEGORY_NAME_TAKEN,
            "uq_category_root_name_key", CategoryErrorCode.CATEGORY_NAME_TAKEN,
            "uq_attribute_template_name", AttributeTemplateErrorCode.ATTRIBUTE_TEMPLATE_NAME_TAKEN,
            "uq_attribute_option_template_value", AttributeTemplateErrorCode.ATTRIBUTE_OPTION_VALUE_TAKEN,
            "uq_brand_name_key", BrandErrorCode.BRAND_NAME_TAKEN,
            "uq_variant_product_combination", VariantErrorCode.VARIANT_COMBINATION_TAKEN,
            "uq_variant_seller_sku", VariantErrorCode.VARIANT_SKU_TAKEN,
            FK_CATEGORY_PARENT, CategoryErrorCode.CATEGORY_HAS_CHILDREN);

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ApiResponse<?> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request,
                                          HttpServletResponse response) {
        String constraint = constraintName(ex);
        ErrorCode errorCode = constraint == null ? null : CONSTRAINT_ERRORS.get(constraint);
        // Cùng khóa ngoài cha, hai nghĩa: XÓA cha còn con (còn con), hoặc CHÈN con dưới cha vừa bị xóa (không còn cha)
        if (FK_CATEGORY_PARENT.equals(constraint) && failedStatementIsInsert(ex)) {
            errorCode = CategoryErrorCode.CATEGORY_NOT_FOUND;
        }
        if (errorCode != null) {
            return reject(errorCode, request, response);
        }
        response.setStatus(500);
        request.setAttribute(GlobalExceptionHandler.ERROR_CODE_KEY, "INTERNAL_ERROR");
        log.error("[PersistenceConflictExceptionHandler] UNMAPPED_CONSTRAINT constraint={}",
                constraint != null ? constraint : "unknown", ex);
        return ApiResponse.error("An unexpected error occurred", "INTERNAL_ERROR");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ApiResponse<?> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request,
                                               HttpServletResponse response) {
        return reject(CatalogErrorCode.CONCURRENT_UPDATE, request, response);
    }

    private static ApiResponse<?> reject(ErrorCode errorCode, HttpServletRequest request, HttpServletResponse response) {
        String code = errorCode instanceof Enum<?> named ? named.name() : errorCode.code();
        response.setStatus(errorCode.httpStatus());
        request.setAttribute(GlobalExceptionHandler.ERROR_CODE_KEY, code);
        log.info("[PersistenceConflictExceptionHandler] {} ({})", code, errorCode.httpStatus());
        return ApiResponse.error(errorCode.defaultMessage(), code);
    }

    /** Câu lệnh bị vi phạm là chèn (không phụ thuộc ngôn ngữ thông báo của máy chủ cơ sở dữ liệu). */
    private static boolean failedStatementIsInsert(Throwable root) {
        for (Throwable current = root; current != null; current = current.getCause()) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException cve && cve.getSQL() != null) {
                return cve.getSQL().stripLeading().toLowerCase(java.util.Locale.ROOT).startsWith("insert");
            }
        }
        return false;
    }

    /** Tên ràng buộc từ ngoại lệ của Hibernate; không có thì tìm trong thông báo gốc của trình điều khiển. */
    private static String constraintName(Throwable root) {
        Throwable current = root;
        Throwable deepest = root;
        while (current != null) {
            deepest = current;
            if (current instanceof org.hibernate.exception.ConstraintViolationException cve
                    && cve.getConstraintName() != null) {
                return cve.getConstraintName();
            }
            current = current.getCause();
        }
        if (deepest.getMessage() != null) {
            Matcher matcher = CONSTRAINT_PATTERN.matcher(deepest.getMessage());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }
}
