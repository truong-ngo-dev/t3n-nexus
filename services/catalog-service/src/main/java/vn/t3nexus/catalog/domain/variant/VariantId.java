package vn.t3nexus.catalog.domain.variant;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

import java.util.UUID;

public class VariantId extends AbstractId<UUID> implements Id<UUID> {

    private VariantId(UUID value) {
        super(value);
    }

    public static VariantId of(UUID id) {
        return new VariantId(id);
    }
}
