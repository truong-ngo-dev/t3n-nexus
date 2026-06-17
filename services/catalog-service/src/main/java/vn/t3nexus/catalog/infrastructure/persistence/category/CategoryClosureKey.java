package vn.t3nexus.catalog.infrastructure.persistence.category;

import java.io.Serializable;
import java.util.Objects;

public class CategoryClosureKey implements Serializable {

    private String ancestorId;
    private String descendantId;

    public CategoryClosureKey() {}

    public CategoryClosureKey(String ancestorId, String descendantId) {
        this.ancestorId   = ancestorId;
        this.descendantId = descendantId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CategoryClosureKey that)) return false;
        return Objects.equals(ancestorId, that.ancestorId)
                && Objects.equals(descendantId, that.descendantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ancestorId, descendantId);
    }
}
