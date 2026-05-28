package vn.t3nexus.lib.common.domain.model;

public abstract class AbstractId<ID> implements Id<ID> {
    protected final ID value;

    public AbstractId(ID value) {
        this.value = value;
    }

    @Override
    public ID getValue() {
        return value;
    }
}
