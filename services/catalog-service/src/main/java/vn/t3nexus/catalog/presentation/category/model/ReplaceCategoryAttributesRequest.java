package vn.t3nexus.catalog.presentation.category.model;

import jakarta.validation.constraints.NotBlank;
import vn.t3nexus.catalog.domain.category.AttributeConstraints;
import vn.t3nexus.catalog.domain.category.Discovery;

/** {@code constraints}/{@code discovery}: bỏ trống (null) = không ràng buộc/không bật khả năng tìm kiếm nào
 *  thêm. Shape hợp lệ phụ thuộc {@code inputType} của template — server kiểm, xem CategoryErrorCode. */
public record ReplaceCategoryAttributesRequest(
        @NotBlank String templateId,
        boolean required,
        int displayOrder,
        AttributeConstraints constraints,
        Discovery discovery
) {}
