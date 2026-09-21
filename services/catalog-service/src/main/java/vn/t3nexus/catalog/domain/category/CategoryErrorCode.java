package vn.t3nexus.catalog.domain.category;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

public enum CategoryErrorCode implements ErrorCode {

    CATEGORY_NOT_FOUND        ("20201", "Category not found",                           "error.category.not_found",             404),
    CATEGORY_SLUG_EXISTS      ("20202", "Category slug already exists",                 "error.category.slug_exists",           409),
    MAX_DEPTH_EXCEEDED        ("20203", "Category tree max depth (3 levels) exceeded",  "error.category.max_depth_exceeded",    422),
    HAS_CHILDREN              ("20204", "Category has sub-categories",                  "error.category.has_children",          409),
    HAS_PRODUCT_REFERENCE     ("20205", "Category is referenced by products",           "error.category.has_product_reference", 409),
    ASSIGNMENT_ALREADY_EXISTS ("20206", "Attribute template appears more than once in the request", "error.category.assignment_exists", 409),
    ATTRIBUTE_ASSIGNMENT_REQUIRES_LEAF ("20208", "Attributes can only be assigned to leaf (L3) categories", "error.category.assignment_requires_leaf", 422),
    CATEGORY_NOT_LEAF         ("20209", "Product must belong to a leaf (L3) category",  "error.category.not_leaf",              422),
    CATEGORY_INACTIVE         ("20210", "Category is inactive",                         "error.category.inactive",              422);

    private final String code;
    private final String defaultMessage;
    private final String messageKey;
    private final int httpStatus;

    CategoryErrorCode(String code, String defaultMessage, String messageKey, int httpStatus) {
        this.code           = code;
        this.defaultMessage = defaultMessage;
        this.messageKey     = messageKey;
        this.httpStatus     = httpStatus;
    }

    @Override public String code()           { return code; }
    @Override public String defaultMessage() { return defaultMessage; }
    @Override public String messageKey()     { return messageKey; }
    @Override public int httpStatus()        { return httpStatus; }
}
