package vn.t3nexus.catalog.domain.product;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

public class ProductId extends AbstractId<String> implements Id<String> {

    private ProductId(String value) {
        super(value);
    }

    public static ProductId of(String id) {
        return new ProductId(id);
    }
}
