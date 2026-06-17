package vn.t3nexus.catalog.domain.product;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

public enum ProductErrorCode implements ErrorCode {

    PRODUCT_NOT_FOUND              ("20301", "Product not found",                                          "error.product.not_found",                    404),
    VARIANT_COMBINATION_EXISTS     ("20302", "Variant with this attribute combination already exists",     "error.product.variant_combination_exists",    409),
    PUBLISH_REQUIRES_ACTIVE_VARIANT("20303", "Product must have at least one active variant to publish",   "error.product.publish_requires_active_variant", 422),
    PRODUCT_BLOCKED                ("20304", "Product is blocked and cannot be modified",                  "error.product.blocked",                      422),
    IMAGE_NOT_FOUND                ("20305", "Image not found",                                            "error.product.image_not_found",              404),
    CATEGORY_LOCKED_AFTER_VARIANT  ("20306", "Category cannot be changed after variants have been added",  "error.product.category_locked_after_variant", 422),
    ATTRIBUTE_NOT_IN_CATEGORY      ("20307", "Attribute template does not belong to product's category",   "error.product.attribute_not_in_category",      422),
    REQUIRED_ATTRIBUTE_MISSING     ("20308", "Required attribute is missing",                              "error.product.required_attribute_missing",      422);

    private final String code;
    private final String defaultMessage;
    private final String messageKey;
    private final int httpStatus;

    ProductErrorCode(String code, String defaultMessage, String messageKey, int httpStatus) {
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
