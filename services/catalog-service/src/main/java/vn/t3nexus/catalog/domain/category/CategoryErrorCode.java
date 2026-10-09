package vn.t3nexus.catalog.domain.category;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

public enum CategoryErrorCode implements ErrorCode {

    CATEGORY_NOT_FOUND        ("20201", "Category not found",                           "error.category.not_found",             404),
    MAX_DEPTH_EXCEEDED        ("20203", "Category tree max depth (3 levels) exceeded",  "error.category.max_depth_exceeded",    422),
    HAS_CHILDREN              ("20204", "Category has sub-categories",                  "error.category.has_children",          409),
    HAS_PRODUCT_REFERENCE     ("20205", "Category is referenced by products",           "error.category.has_product_reference", 409),
    ASSIGNMENT_ALREADY_EXISTS ("20206", "Attribute template appears more than once in the request", "error.category.assignment_exists", 409),
    ATTRIBUTE_ASSIGNMENT_REQUIRES_LEAF ("20208", "Attributes can only be assigned to leaf (L3) categories", "error.category.assignment_requires_leaf", 422),
    CATEGORY_NOT_LEAF         ("20209", "Product must belong to a leaf (L3) category",  "error.category.not_leaf",              422),
    CATEGORY_INACTIVE         ("20210", "Category is inactive",                         "error.category.inactive",              422),
    CATEGORY_NAME_EXISTS_IN_PARENT ("20211", "Category name already exists under this parent", "error.category.name_exists_in_parent", 409),
    INVALID_CONSTRAINTS       ("20212", "Attribute constraints invalid for its input type", "error.category.invalid_constraints", 422),
    INVALID_DISCOVERY         ("20213", "Attribute discovery config invalid for its input type", "error.category.invalid_discovery", 422),
    INVALID_RANGE_FILTER      ("20214", "Range filter config invalid",                  "error.category.invalid_range_filter",  422),
    INVALID_CATEGORY_ORDER    ("20215", "Category order must contain every sibling exactly once", "error.category.invalid_order", 422);

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
