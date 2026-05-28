package vn.t3nexus.lib.common.domain.model;

import java.util.Objects;

/**
 * Base implementation for domain entities.
 * <br>Handles identity and equality based on the domain identifier.
 *
 * @param <T> the type of the identifier
 */
public abstract class AbstractEntity<T extends Id<?>> extends SurrogateIdentity implements Entity<T> {

    private T id;

    @Override
    public T getId() {
        return id;
    }

    @Override
    public void setId(T id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AbstractEntity<?> that = (AbstractEntity<?>) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}