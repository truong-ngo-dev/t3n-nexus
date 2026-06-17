package vn.t3nexus.catalog.domain.variant;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

public class SkuImageId extends AbstractId<String> implements Id<String> {

    private SkuImageId(String value) {
        super(value);
    }

    public static SkuImageId of(String id) {
        return new SkuImageId(id);
    }
}
