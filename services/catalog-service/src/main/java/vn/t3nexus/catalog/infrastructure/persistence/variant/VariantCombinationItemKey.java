package vn.t3nexus.catalog.infrastructure.persistence.variant;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Khóa chính của {@code variant_combination_item}: (biến thể, mẫu): mỗi biến thể một giá trị cho mỗi trục. */
public class VariantCombinationItemKey implements Serializable {

    private UUID variantId;
    private UUID templateId;

    public VariantCombinationItemKey() {}

    public VariantCombinationItemKey(UUID variantId, UUID templateId) {
        this.variantId = variantId;
        this.templateId = templateId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VariantCombinationItemKey that)) return false;
        return Objects.equals(variantId, that.variantId) && Objects.equals(templateId, that.templateId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variantId, templateId);
    }
}
