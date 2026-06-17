package vn.t3nexus.catalog.domain.category;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

public enum CategoryErrorCode implements ErrorCode {

    CATEGORY_NOT_FOUND        ("20201", "Category not found",                           "error.category.not_found",             404),
    CATEGORY_SLUG_EXISTS      ("20202", "Category slug already exists",                 "error.category.slug_exists",           409),
    MAX_DEPTH_EXCEEDED        ("20203", "Category tree max depth (3 levels) exceeded",  "error.category.max_depth_exceeded",    422),
    HAS_CHILDREN              ("20204", "Category has sub-categories",                  "error.category.has_children",          409),
    HAS_PRODUCT_REFERENCE     ("20205", "Category is referenced by products",           "error.category.has_product_reference", 409),
    ASSIGNMENT_ALREADY_EXISTS ("20206", "Attribute template already assigned",          "error.category.assignment_exists",     409),
    ASSIGNMENT_NOT_FOUND      ("20207", "Attribute assignment not found",               "error.category.assignment_not_found",  404);

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
