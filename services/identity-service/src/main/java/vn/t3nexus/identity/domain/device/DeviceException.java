package vn.t3nexus.identity.domain.device;

import vn.t3nexus.lib.common.domain.exception.DomainException;

public class DeviceException extends DomainException {

    public DeviceException(DeviceErrorCode errorCode) {
        super(errorCode);
    }

    public static DeviceException notFound()            { return new DeviceException(DeviceErrorCode.DEVICE_NOT_FOUND); }
    public static DeviceException notActive()           { return new DeviceException(DeviceErrorCode.DEVICE_NOT_ACTIVE); }
    public static DeviceException notTrusted()          { return new DeviceException(DeviceErrorCode.DEVICE_NOT_TRUSTED); }
    public static DeviceException alreadyTrusted()      { return new DeviceException(DeviceErrorCode.DEVICE_ALREADY_TRUSTED); }
    public static DeviceException notBelongToUser()     { return new DeviceException(DeviceErrorCode.DEVICE_NOT_BELONG_TO_USER); }
    public static DeviceException cannotRevokeCurrent() { return new DeviceException(DeviceErrorCode.CANNOT_REVOKE_CURRENT); }
    public static DeviceException fingerprintInvalid()  { return new DeviceException(DeviceErrorCode.DEVICE_FINGERPRINT_INVALID); }
    public static DeviceException nameTooLong()         { return new DeviceException(DeviceErrorCode.DEVICE_NAME_TOO_LONG); }
}
