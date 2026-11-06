package vn.t3nexus.catalog.domain.category;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

import java.util.UUID;

public class CategoryId extends AbstractId<UUID> implements Id<UUID> {

    private CategoryId(UUID value) {
        super(value);
    }

    public static CategoryId of(UUID id) {
        return new CategoryId(id);
    }
}
