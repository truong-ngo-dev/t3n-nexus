package vn.t3nexus.lib.common.domain.vo;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

public class UserId extends AbstractId<String> implements Id<String> {
    private UserId(String value) { super(value); }

    public static UserId of(String value) { return new UserId(value); }
}
