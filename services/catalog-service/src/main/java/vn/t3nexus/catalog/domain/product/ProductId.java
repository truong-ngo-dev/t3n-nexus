package vn.t3nexus.catalog.domain.product;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

import java.util.UUID;

public class ProductId extends AbstractId<UUID> implements Id<UUID> {

    private ProductId(UUID value) {
        super(value);
    }

    public static ProductId of(UUID id) {
        return new ProductId(id);
    }
}
