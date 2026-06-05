package vn.t3nexus.identity.domain.device;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

public enum DeviceErrorCode implements ErrorCode {

    DEVICE_NOT_FOUND           ("12001", "Device not found",                        "error.device.not_found",              404),
    DEVICE_NOT_ACTIVE          ("12002", "Device is not active",                    "error.device.not_active",             409),
    DEVICE_NAME_TOO_LONG       ("12003", "Device name is too long",                 "error.device.name_too_long",          400),
    DEVICE_NOT_TRUSTED         ("12004", "Device is not trusted",                   "error.device.not_trusted",            403),
    DEVICE_ALREADY_TRUSTED     ("12005", "Device is already trusted",               "error.device.already_trusted",        409),
    DEVICE_NOT_BELONG_TO_USER  ("12006", "Device does not belong to user",          "error.device.not_belong_to_user",     403),
    CANNOT_REVOKE_CURRENT      ("12007", "Cannot revoke the current device",        "error.device.cannot_revoke_current",  400),
    DEVICE_FINGERPRINT_INVALID ("12008", "Device fingerprint is invalid",           "error.device.fingerprint_invalid",    400);

    private final String code;
    private final String defaultMessage;
    private final String messageKey;
    private final int    httpStatus;

    DeviceErrorCode(String code, String defaultMessage, String messageKey, int httpStatus) {
        this.code           = code;
        this.defaultMessage = defaultMessage;
        this.messageKey     = messageKey;
        this.httpStatus     = httpStatus;
    }

    @Override public String code()           { return code; }
    @Override public String defaultMessage() { return defaultMessage; }
    @Override public String messageKey()     { return messageKey; }
    @Override public int    httpStatus()     { return httpStatus; }
}
