package vn.t3nexus.catalog.infrastructure.persistence.product;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Khóa chính của {@code product_attribute_option}: (sản phẩm, mẫu, tùy chọn). */
public class ProductAttributeOptionKey implements Serializable {

    private UUID productId;
    private UUID templateId;
    private UUID optionId;

    public ProductAttributeOptionKey() {}

    public ProductAttributeOptionKey(UUID productId, UUID templateId, UUID optionId) {
        this.productId = productId;
        this.templateId = templateId;
        this.optionId = optionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductAttributeOptionKey that)) return false;
        return Objects.equals(productId, that.productId) && Objects.equals(templateId, that.templateId)
                && Objects.equals(optionId, that.optionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, templateId, optionId);
    }
}
