package vn.t3nexus.inventory.domain.limitedoffer;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

public class LimitedOfferId extends AbstractId<String> implements Id<String> {

    private LimitedOfferId(String value) {
        super(value);
    }

    public static LimitedOfferId of(String id) {
        return new LimitedOfferId(id);
    }
}
