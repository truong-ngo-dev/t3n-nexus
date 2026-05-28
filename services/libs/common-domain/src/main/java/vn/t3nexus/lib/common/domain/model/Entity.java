package vn.t3nexus.lib.common.domain.model;

/**
 * Represents a domain object with a unique identity that persists over time.
 *
 * @param <T> the type of the identifier
 */
public interface Entity<T extends Id<?>> extends DomainObject {
    /**
     * @return the unique identifier of this entity
     */
    T getId();

    /**
     * @param id the unique identifier to set
     */
    void setId(T id);
}
