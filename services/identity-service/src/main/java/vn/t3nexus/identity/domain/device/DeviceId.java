package vn.t3nexus.identity.domain.device;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

public class DeviceId extends AbstractId<String> implements Id<String> {

    private DeviceId(String value) { super(value); }

    public static DeviceId of(String value) { return new DeviceId(value); }
}
