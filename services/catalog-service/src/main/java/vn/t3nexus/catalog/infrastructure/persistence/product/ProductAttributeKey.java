package vn.t3nexus.catalog.infrastructure.persistence.product;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Khóa chính của {@code product_attribute}: (sản phẩm, mẫu). */
public class ProductAttributeKey implements Serializable {

    private UUID productId;
    private UUID templateId;

    public ProductAttributeKey() {}

    public ProductAttributeKey(UUID productId, UUID templateId) {
        this.productId = productId;
        this.templateId = templateId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductAttributeKey that)) return false;
        return Objects.equals(productId, that.productId) && Objects.equals(templateId, that.templateId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, templateId);
    }
}
