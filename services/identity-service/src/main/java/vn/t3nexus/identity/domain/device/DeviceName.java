package vn.t3nexus.identity.domain.device;

import vn.t3nexus.lib.common.domain.model.ValueObject;
import vn.t3nexus.lib.utils.lang.Assert;

public class DeviceName implements ValueObject {

    private final String     value;
    private final DeviceType type;

    private DeviceName(String value, DeviceType type) {
        this.value = value;
        this.type  = type;
    }

    public static DeviceName of(String value, DeviceType type) {
        Assert.hasText(value, "DeviceName must not be empty");
        return new DeviceName(value, type);
    }

    public static DeviceName unknown() {
        return new DeviceName("Unknown Device", DeviceType.OTHER);
    }

    public String     getValue() { return value; }
    public DeviceType getType()  { return type; }
}
