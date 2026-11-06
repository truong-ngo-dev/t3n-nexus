package vn.t3nexus.catalog.domain.category;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

public enum CategoryErrorCode implements ErrorCode {

    CATEGORY_NOT_FOUND              ("CATEGORY_NOT_FOUND",              "Không tìm thấy danh mục",                                  "error.category.not_found",               404),
    CATEGORY_MAX_DEPTH_EXCEEDED     ("CATEGORY_MAX_DEPTH_EXCEEDED",     "Cây danh mục tối đa ba cấp",                               "error.category.max_depth_exceeded",      409),
    CATEGORY_NOT_LEAF               ("CATEGORY_NOT_LEAF",               "Chỉ danh mục cấp ba (lá) mới có thuộc tính được gán",      "error.category.not_leaf",                409),
    CATEGORY_NAME_TAKEN             ("CATEGORY_NAME_TAKEN",             "Tên danh mục đã được dùng trong cùng cha",                 "error.category.name_taken",              409),
    CATEGORY_HAS_CHILDREN           ("CATEGORY_HAS_CHILDREN",           "Danh mục còn danh mục con; hãy ngừng dùng thay vì xóa",    "error.category.has_children",            409),
    CATEGORY_HAS_PRODUCTS           ("CATEGORY_HAS_PRODUCTS",           "Danh mục đang có sản phẩm; hãy ngừng dùng thay vì xóa",    "error.category.has_products",            409),
    CATEGORY_INVALID_ORDER          ("CATEGORY_INVALID_ORDER",          "Thứ tự phải nêu mọi danh mục cùng cha đúng một lần",       "error.category.invalid_order",           409),
    ATTRIBUTE_ASSIGNMENT_DUPLICATED ("ATTRIBUTE_ASSIGNMENT_DUPLICATED", "Một mẫu thuộc tính xuất hiện nhiều lần trong danh sách gán", "error.category.assignment_duplicated",   409),
    ATTRIBUTE_CONSTRAINTS_INVALID   ("ATTRIBUTE_CONSTRAINTS_INVALID",   "Ràng buộc không hợp với kiểu nhập của mẫu thuộc tính",     "error.category.constraints_invalid",     400);

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
