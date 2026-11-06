package vn.t3nexus.catalog.domain.attributetemplate;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

public enum AttributeTemplateErrorCode implements ErrorCode {

    ATTRIBUTE_TEMPLATE_NOT_FOUND    ("ATTRIBUTE_TEMPLATE_NOT_FOUND",     "Không tìm thấy mẫu thuộc tính",                         "error.attribute_template.not_found",             404),
    ATTRIBUTE_TEMPLATE_NAME_TAKEN   ("ATTRIBUTE_TEMPLATE_NAME_TAKEN",    "Tên mẫu thuộc tính đã được sử dụng",                    "error.attribute_template.name_taken",            409),
    ATTRIBUTE_TEMPLATE_INACTIVE     ("ATTRIBUTE_TEMPLATE_INACTIVE",      "Mẫu thuộc tính đã ngừng dùng",                          "error.attribute_template.inactive",              409),
    ATTRIBUTE_OPTION_NOT_FOUND      ("ATTRIBUTE_OPTION_NOT_FOUND",       "Không tìm thấy tùy chọn",                               "error.attribute_option.not_found",               404),
    ATTRIBUTE_OPTION_VALUE_TAKEN    ("ATTRIBUTE_OPTION_VALUE_TAKEN",     "Giá trị tùy chọn đã có trong mẫu",                      "error.attribute_option.value_taken",             409),
    ATTRIBUTE_INPUT_TYPE_NOT_SELECT ("ATTRIBUTE_INPUT_TYPE_NOT_SELECT",  "Chỉ mẫu kiểu chọn mới có tùy chọn",                     "error.attribute_template.not_select",            409),
    ATTRIBUTE_INVALID_OPTION_ORDER  ("ATTRIBUTE_INVALID_OPTION_ORDER",   "Thứ tự phải nêu mọi tùy chọn của mẫu đúng một lần",     "error.attribute_template.invalid_option_order",  400);

    private final String code;
    private final String defaultMessage;
    private final String messageKey;
    private final int httpStatus;

    AttributeTemplateErrorCode(String code, String defaultMessage, String messageKey, int httpStatus) {
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
