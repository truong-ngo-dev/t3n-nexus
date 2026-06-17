package vn.t3nexus.catalog.domain.variant;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

public class VariantId extends AbstractId<String> implements Id<String> {

    private VariantId(String value) {
        super(value);
    }

    public static VariantId of(String id) {
        return new VariantId(id);
    }
}
