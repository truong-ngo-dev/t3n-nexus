package vn.t3nexus.catalog.infrastructure.persistence.product;

import java.io.Serializable;
import java.util.Objects;

public class ProductAttributeValueKey implements Serializable {

    private String productId;
    private String templateId;

    public ProductAttributeValueKey() {}

    public ProductAttributeValueKey(String productId, String templateId) {
        this.productId  = productId;
        this.templateId = templateId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductAttributeValueKey that)) return false;
        return Objects.equals(productId, that.productId)
                && Objects.equals(templateId, that.templateId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, templateId);
    }
}
