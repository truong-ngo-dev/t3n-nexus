package vn.t3nexus.catalog.domain.product;

import vn.t3nexus.lib.common.domain.model.AbstractEntity;
import vn.t3nexus.lib.common.domain.model.Entity;

public class ProductImage extends AbstractEntity<ProductImageId> implements Entity<ProductImageId> {

    private final String objectKey;
    private int displayOrder;

    private ProductImage(ProductImageId id, String objectKey, int displayOrder) {
        setId(id);
        this.objectKey    = objectKey;
        this.displayOrder = displayOrder;
    }

    public static ProductImage create(ProductImageId id, String objectKey, int displayOrder) {
        return new ProductImage(id, objectKey, displayOrder);
    }

    public static ProductImage reconstitute(ProductImageId id, String objectKey, int displayOrder) {
        return new ProductImage(id, objectKey, displayOrder);
    }

    public String getObjectKey()    { return objectKey; }
    public int getDisplayOrder()    { return displayOrder; }
}
