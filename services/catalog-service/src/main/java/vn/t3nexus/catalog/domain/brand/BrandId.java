package vn.t3nexus.catalog.domain.brand;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

public class BrandId extends AbstractId<String> implements Id<String> {

    private BrandId(String value) {
        super(value);
    }

    public static BrandId of(String id) {
        return new BrandId(id);
    }
}
