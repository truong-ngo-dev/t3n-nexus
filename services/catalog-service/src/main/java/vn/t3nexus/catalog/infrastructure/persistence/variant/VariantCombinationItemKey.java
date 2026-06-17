package vn.t3nexus.catalog.infrastructure.persistence.variant;

import java.io.Serializable;
import java.util.Objects;

public class VariantCombinationItemKey implements Serializable {

    private String variantId;
    private String templateId;
    private String optionId;

    public VariantCombinationItemKey() {}

    public VariantCombinationItemKey(String variantId, String templateId, String optionId) {
        this.variantId  = variantId;
        this.templateId = templateId;
        this.optionId   = optionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VariantCombinationItemKey that)) return false;
        return Objects.equals(variantId, that.variantId)
                && Objects.equals(templateId, that.templateId)
                && Objects.equals(optionId, that.optionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variantId, templateId, optionId);
    }
}
