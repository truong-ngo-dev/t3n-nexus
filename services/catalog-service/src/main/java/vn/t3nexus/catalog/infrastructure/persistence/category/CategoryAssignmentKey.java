package vn.t3nexus.catalog.infrastructure.persistence.category;

import java.io.Serializable;
import java.util.Objects;

public class CategoryAssignmentKey implements Serializable {

    private String categoryId;
    private String templateId;

    public CategoryAssignmentKey() {}

    public CategoryAssignmentKey(String categoryId, String templateId) {
        this.categoryId = categoryId;
        this.templateId = templateId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CategoryAssignmentKey that)) return false;
        return Objects.equals(categoryId, that.categoryId)
                && Objects.equals(templateId, that.templateId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(categoryId, templateId);
    }
}
