package vn.t3nexus.catalog.domain.attributetemplate;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

public enum AttributeTemplateErrorCode implements ErrorCode {

    TEMPLATE_NOT_FOUND    ("20101", "Attribute template not found",              "error.attribute_template.not_found",     404),
    TEMPLATE_NAME_EXISTS  ("20102", "Attribute template name already exists",    "error.attribute_template.name_exists",   409),
    OPTION_NOT_FOUND      ("20103", "Attribute option not found",                "error.attribute_option.not_found",       404),
    OPTION_IN_USE         ("20104", "Attribute option is used by a variant",     "error.attribute_option.in_use",          409),
    INPUT_TYPE_NOT_SELECT        ("20105", "Options are only allowed for SELECT type",             "error.attribute_template.not_select",           422),
    GLOBAL_TEMPLATE_NOT_ASSIGNABLE ("20106", "GLOBAL templates apply to all categories automatically", "error.attribute_template.global_not_assignable", 422);

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
