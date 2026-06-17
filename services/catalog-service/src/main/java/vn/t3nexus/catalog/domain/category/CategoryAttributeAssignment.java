package vn.t3nexus.catalog.domain.category;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.lib.common.domain.model.ValueObject;

import java.util.Objects;

public class CategoryAttributeAssignment implements ValueObject {

    private final AttributeTemplateId attributeTemplateId;
    private final boolean isVariantDefining;
    private final boolean isRequired;
    private final boolean isFilterable;
    private final int displayOrder;

    public CategoryAttributeAssignment(AttributeTemplateId attributeTemplateId,
                                       boolean isVariantDefining,
                                       boolean isRequired,
                                       boolean isFilterable,
                                       int displayOrder) {
        this.attributeTemplateId = attributeTemplateId;
        this.isVariantDefining   = isVariantDefining;
        this.isRequired          = isRequired;
        this.isFilterable        = isFilterable;
        this.displayOrder        = displayOrder;
    }

    public AttributeTemplateId getAttributeTemplateId() { return attributeTemplateId; }
    public boolean isVariantDefining()                  { return isVariantDefining; }
    public boolean isRequired()                         { return isRequired; }
    public boolean isFilterable()                       { return isFilterable; }
    public int getDisplayOrder()                        { return displayOrder; }

    // Equality is solely by templateId — two assignments to same template are duplicates
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CategoryAttributeAssignment other)) return false;
        return Objects.equals(attributeTemplateId, other.attributeTemplateId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(attributeTemplateId);
    }
}
