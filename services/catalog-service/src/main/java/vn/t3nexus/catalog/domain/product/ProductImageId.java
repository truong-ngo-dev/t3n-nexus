package vn.t3nexus.catalog.domain.product;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

import java.util.UUID;

public class ProductImageId extends AbstractId<UUID> implements Id<UUID> {

    private ProductImageId(UUID value) {
        super(value);
    }

    public static ProductImageId of(UUID id) {
        return new ProductImageId(id);
    }
}
