package vn.t3nexus.lib.common.domain.model;

import java.util.Objects;

public abstract class AbstractId<ID> implements Id<ID> {
    protected final ID value;

    public AbstractId(ID value) {
        this.value = value;
    }

    @Override
    public ID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AbstractId<?> that = (AbstractId<?>) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
