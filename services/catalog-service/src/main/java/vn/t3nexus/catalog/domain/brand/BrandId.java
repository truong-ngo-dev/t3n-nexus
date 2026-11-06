package vn.t3nexus.catalog.domain.brand;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

import java.util.UUID;

public class BrandId extends AbstractId<UUID> implements Id<UUID> {

    private BrandId(UUID value) {
        super(value);
    }

    public static BrandId of(UUID id) {
        return new BrandId(id);
    }
}
