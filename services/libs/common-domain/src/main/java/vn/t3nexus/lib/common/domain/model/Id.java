package vn.t3nexus.lib.common.domain.model;

/**
 * Represents a unique identifier for an {@link Entity}.
 *
 * @param <ID> the type of the underlying ID value (e.g., UUID, Long, String)
 */
public interface Id<ID> extends ValueObject {

    /**
     * @return the raw value of the identifier
     */
    ID getValue();

    /**
     * @return the string representation of the identifier value
     */
    default String getValueAsString() {
        return getValue().toString();
    }
}
