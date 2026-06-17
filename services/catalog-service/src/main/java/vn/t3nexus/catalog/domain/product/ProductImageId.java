package vn.t3nexus.catalog.domain.product;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

public class ProductImageId extends AbstractId<String> implements Id<String> {

    private ProductImageId(String value) {
        super(value);
    }

    public static ProductImageId of(String id) {
        return new ProductImageId(id);
    }
}
