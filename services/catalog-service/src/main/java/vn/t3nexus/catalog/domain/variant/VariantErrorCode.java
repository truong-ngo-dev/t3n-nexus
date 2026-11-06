package vn.t3nexus.catalog.domain.variant;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

/** Mã lỗi của biến thể. 404 không có (kể cả biến thể của sản phẩm khác), 409 vi phạm quy tắc, 400 tổ hợp sai. */
public enum VariantErrorCode implements ErrorCode {

    VARIANT_NOT_FOUND           ("VARIANT_NOT_FOUND",           "Không tìm thấy biến thể",                      "error.variant.not_found",           404),
    VARIANT_COMBINATION_INVALID ("VARIANT_COMBINATION_INVALID", "Tổ hợp biến thể không khớp trục của sản phẩm", "error.variant.combination_invalid", 400),
    VARIANT_COMBINATION_TAKEN   ("VARIANT_COMBINATION_TAKEN",   "Tổ hợp biến thể đã tồn tại trong sản phẩm",    "error.variant.combination_taken",   409),
    VARIANT_LIMIT_REACHED       ("VARIANT_LIMIT_REACHED",       "Sản phẩm đã đạt số biến thể tối đa",           "error.variant.limit_reached",       409),
    VARIANT_SKU_TAKEN           ("VARIANT_SKU_TAKEN",           "Mã SKU đã được người bán sử dụng",             "error.variant.sku_taken",           409);

    private final String code;
    private final String defaultMessage;
    private final String messageKey;
    private final int httpStatus;

    VariantErrorCode(String code, String defaultMessage, String messageKey, int httpStatus) {
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
