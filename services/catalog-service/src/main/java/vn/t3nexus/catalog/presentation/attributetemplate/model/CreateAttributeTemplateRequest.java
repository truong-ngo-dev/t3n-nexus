package vn.t3nexus.catalog.presentation.attributetemplate.model;

import jakarta.validation.constraints.NotNull;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;

/** Quy tắc nội dung (rỗng, độ dài, đơn vị chỉ cho kiểu số) kiểm ở miền và trả 400 {@code CATALOG_FIELD_INVALID}. */
public record CreateAttributeTemplateRequest(
        String name,
        String displayName,
        String hint,
        @NotNull InputType inputType,
        String unit
) {}
