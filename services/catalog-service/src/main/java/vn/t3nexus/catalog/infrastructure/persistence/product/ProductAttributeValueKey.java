package vn.t3nexus.catalog.infrastructure.persistence.product;

import java.io.Serializable;
import java.util.Objects;

public class ProductAttributeValueKey implements Serializable {

    private String productId;
    private String templateId;
    private String value;

    public ProductAttributeValueKey() {}

    public ProductAttributeValueKey(String productId, String templateId, String value) {
        this.productId  = productId;
        this.templateId = templateId;
        this.value      = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductAttributeValueKey that)) return false;
        return Objects.equals(productId, that.productId)
                && Objects.equals(templateId, that.templateId)
                && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, templateId, value);
    }
}
