package vn.t3nexus.lib.common.domain.exception;

/**
 * Base class for all domain-specific exceptions.
 * <br>Domain exceptions represent business rule violations or invalid domain states.
 */
public class DomainException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object[] args;

    /**
     * Dùng defaultMessage từ ErrorCode
     * VD: throw new DomainException(DeviceErrorCode.DEVICE_NOT_ACTIVE)
     */
    public DomainException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
        this.args = new Object[0];
    }

    /**
     * Dùng defaultMessage + args cho i18n placeholder
     * VD: throw new DomainException(DeviceErrorCode.DEVICE_NAME_TOO_LONG, 255, 300)
     * log  → "Device name is too long"
     * i18n → "Tên thiết bị không được vượt quá 255 ký tự, hiện tại: 300"
     */
    public DomainException(ErrorCode errorCode, Object... args) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
        this.args = args;
    }

    /**
     * Dùng custom message đã resolved thay thế defaultMessage
     * args mặc định rỗng vì message đã resolved sẵn
     * VD: throw new DomainException(DeviceErrorCode.DEVICE_NOT_FOUND, "Device abc-123 not found")
     * log  → "Device abc-123 not found"
     * i18n → messageSource resolve theo messageKey, không có args
     */
    public DomainException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.args = new Object[0];
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Object[] getArgs() {
        return args;
    }
}

