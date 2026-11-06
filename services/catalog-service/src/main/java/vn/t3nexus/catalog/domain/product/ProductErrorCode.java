package vn.t3nexus.catalog.domain.product;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

/** Mã lỗi của sản phẩm. 404 không có (kể cả sản phẩm của người khác), 409 vi phạm quy tắc hoặc trạng thái, 400 dữ liệu sai. */
public enum ProductErrorCode implements ErrorCode {

    PRODUCT_NOT_FOUND           ("PRODUCT_NOT_FOUND",           "Không tìm thấy sản phẩm",                                         "error.product.not_found",           404),
    PRODUCT_CATEGORY_NOT_USABLE ("PRODUCT_CATEGORY_NOT_USABLE", "Danh mục không phải lá hoặc không dùng được",                   "error.product.category_not_usable", 409),
    PRODUCT_BRAND_NOT_USABLE    ("PRODUCT_BRAND_NOT_USABLE",    "Thương hiệu đã ngừng dùng",                                       "error.product.brand_not_usable",    409),
    PRODUCT_AXES_LOCKED         ("PRODUCT_AXES_LOCKED",         "Sản phẩm đã có biến thể, không đổi được tập thuộc tính làm trục", "error.product.axes_locked",         409),
    PRODUCT_AXIS_VALUE_IN_USE   ("PRODUCT_AXIS_VALUE_IN_USE",   "Giá trị trục đang được biến thể sử dụng",                         "error.product.axis_value_in_use",   409),
    PRODUCT_AXIS_INVALID        ("PRODUCT_AXIS_INVALID",        "Trục biến thể không hợp lệ",                                      "error.product.axis_invalid",        400),
    PRODUCT_ATTRIBUTE_INVALID   ("PRODUCT_ATTRIBUTE_INVALID",   "Giá trị thuộc tính không hợp lệ",                                 "error.product.attribute_invalid",   400),
    IMAGE_LIMIT_REACHED         ("IMAGE_LIMIT_REACHED",         "Sản phẩm đã đủ số ảnh tối đa",                                    "error.product.image_limit_reached", 409),
    IMAGE_NOT_FOUND             ("IMAGE_NOT_FOUND",             "Không tìm thấy ảnh",                                              "error.product.image_not_found",     404),
    IMAGE_ORDER_INVALID         ("IMAGE_ORDER_INVALID",         "Thứ tự ảnh phải nêu đúng mọi ảnh một lần",                        "error.product.image_order_invalid", 409),
    SELLER_NOT_APPROVED         ("SELLER_NOT_APPROVED",         "Người bán chưa được duyệt",                                       "error.product.seller_not_approved", 403),
    PRODUCT_NOT_PUBLISHABLE     ("PRODUCT_NOT_PUBLISHABLE",     "Sản phẩm chưa đủ điều kiện đăng",                                 "error.product.not_publishable",     409),
    PRODUCT_NOT_DELETABLE       ("PRODUCT_NOT_DELETABLE",       "Chỉ xóa được nháp chưa từng đăng",                                "error.product.not_deletable",       409),
    PRODUCT_LAST_IMAGE          ("PRODUCT_LAST_IMAGE",          "Sản phẩm đã đăng phải còn ít nhất một ảnh",                        "error.product.last_image",          409);

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
