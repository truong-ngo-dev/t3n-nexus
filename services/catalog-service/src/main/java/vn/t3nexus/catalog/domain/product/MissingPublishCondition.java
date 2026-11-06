package vn.t3nexus.catalog.domain.product;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;

/** Một điều kiện còn thiếu để đăng (RM-CAT-04). {@code templateId} có khi thiếu một thuộc tính bắt buộc. */
public record MissingPublishCondition(Code code, AttributeTemplateId templateId) {

    public enum Code {
        REQUIRED_ATTRIBUTE_MISSING,
        NO_ACTIVE_VARIANT,
        NO_IMAGE,
        SELLER_NOT_APPROVED,
        CATEGORY_NOT_USABLE
    }
}
