package vn.t3nexus.catalog.domain.variant;

import vn.t3nexus.lib.common.domain.model.AbstractEntity;
import vn.t3nexus.lib.common.domain.model.Entity;

public class SkuImage extends AbstractEntity<SkuImageId> implements Entity<SkuImageId> {

    private final String objectKey;
    private int displayOrder;

    private SkuImage(SkuImageId id, String objectKey, int displayOrder) {
        setId(id);
        this.objectKey    = objectKey;
        this.displayOrder = displayOrder;
    }

    public static SkuImage create(SkuImageId id, String objectKey, int displayOrder) {
        return new SkuImage(id, objectKey, displayOrder);
    }

    public static SkuImage reconstitute(SkuImageId id, String objectKey, int displayOrder) {
        return new SkuImage(id, objectKey, displayOrder);
    }

    public String getObjectKey()  { return objectKey; }
    public int getDisplayOrder()  { return displayOrder; }
}
