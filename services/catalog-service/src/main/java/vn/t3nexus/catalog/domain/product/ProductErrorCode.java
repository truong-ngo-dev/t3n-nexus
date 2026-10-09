package vn.t3nexus.catalog.domain.product;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

public enum ProductErrorCode implements ErrorCode {

    PRODUCT_NOT_FOUND              ("20301", "Product not found",                                          "error.product.not_found",                    404),
    VARIANT_COMBINATION_EXISTS     ("20302", "Variant with this attribute combination already exists",     "error.product.variant_combination_exists",    409),
    PUBLISH_REQUIRES_ACTIVE_VARIANT("20303", "Product must have at least one active variant to publish",   "error.product.publish_requires_active_variant", 422),
    PRODUCT_BLOCKED                ("20304", "Product is blocked and cannot be published",                 "error.product.blocked",                      422),
    IMAGE_NOT_FOUND                ("20305", "Image not found",                                            "error.product.image_not_found",              404),
    CATEGORY_LOCKED_AFTER_VARIANT  ("20306", "Category cannot be changed after variants have been added",  "error.product.category_locked_after_variant", 422),
    ATTRIBUTE_NOT_IN_CATEGORY      ("20307", "Attribute template does not belong to product's category",   "error.product.attribute_not_in_category",      422),
    REQUIRED_ATTRIBUTE_MISSING     ("20308", "Required attribute is missing",                              "error.product.required_attribute_missing",      422),
    INVALID_ATTRIBUTE_VALUE        ("20309", "Value is not an active option of this SELECT attribute",     "error.product.invalid_attribute_value",         422),
    VARIANT_OPTION_NOT_DECLARED_BY_PRODUCT("20310", "Variant option value is not part of the product's declared attribute values", "error.product.variant_option_not_declared", 422),
    ATTRIBUTE_VALUE_STILL_USED_BY_VARIANT("20311", "Attribute value cannot be removed while a variant still uses it", "error.product.attribute_value_still_used_by_variant", 422),
    VARIANT_MISSING_VARIANT_DEFINING_ATTRIBUTE("20312", "Variant combination is missing a value for an attribute marked variant-defining", "error.product.variant_missing_variant_defining_attribute", 422),
    VARIANT_DEFINING_REQUIRES_SELECT("20313", "isVariantDefining can only be true for a SINGLE_SELECT attribute", "error.product.variant_defining_requires_select", 422),
    VARIANT_DEFINING_LOCKED_AFTER_VARIANT("20314", "isVariantDefining cannot change once the product has a variant", "error.product.variant_defining_locked_after_variant", 422),
    ATTRIBUTE_VALUE_FORMAT_INVALID("20315", "Value does not match the attribute's inputType format", "error.product.attribute_value_format_invalid", 422),
    PRODUCT_NOT_DRAFT("20316", "Only a DRAFT product (never published) can be permanently deleted", "error.product.not_draft", 422),
    NOT_PRODUCT_OWNER("20317", "Product belongs to another seller", "error.product.not_owner", 403),
    INVALID_PRODUCT_TRANSITION("20318", "Action is not allowed in the product's current state", "error.product.invalid_transition", 409),
    ATTRIBUTE_VALUE_CONSTRAINT_VIOLATED("20319", "Value violates the category constraints for this attribute", "error.product.attribute_value_constraint_violated", 422),
    VARIANT_DUPLICATE_AXIS("20320", "Variant combination has more than one value for the same axis", "error.product.variant_duplicate_axis", 422),
    VARIANT_ATTRIBUTE_NOT_AXIS("20321", "Variant combination contains an attribute that is not a variant-defining axis of the product", "error.product.variant_attribute_not_axis", 422),
    VARIANT_PRICE_INVALID("20322", "Variant price must be greater than zero", "error.product.variant_price_invalid", 422);

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
