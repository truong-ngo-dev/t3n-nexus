package vn.t3nexus.catalog.infrastructure.persistence.product;

import java.io.Serializable;
import java.util.Objects;

public class ProductVariantDefiningAttributeKey implements Serializable {

    private String productId;
    private String templateId;

    public ProductVariantDefiningAttributeKey() {}

    public ProductVariantDefiningAttributeKey(String productId, String templateId) {
        this.productId  = productId;
        this.templateId = templateId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductVariantDefiningAttributeKey that)) return false;
        return Objects.equals(productId, that.productId)
                && Objects.equals(templateId, that.templateId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, templateId);
    }
}
