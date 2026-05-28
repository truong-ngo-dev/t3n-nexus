package vn.t3nexus.lib.common.domain.service;

import vn.t3nexus.lib.common.domain.model.AggregateRoot;
import vn.t3nexus.lib.common.domain.model.Id;

import java.util.Optional;

/**
 * Generic repository interface for managing {@link AggregateRoot} life cycle.
 * <br>Repositories provide an abstraction for persisting and retrieving aggregates from a data store.
 *
 * @param <T>  the type of the aggregate root
 * @param <ID> the type of the aggregate identifier
 */
public interface Repository<T extends AggregateRoot<?>, ID extends Id<?>> {
    /**
     * Finds an aggregate root by its identifier.
     *
     * @param id the identifier to search for
     * @return an {@link Optional} containing the found aggregate, or empty if not found
     */
    Optional<T> findById(ID id);

    /**
     * Saves or updates an aggregate root.
     * <br>Implementations should ensure that domain events are published or handled appropriately.
     *
     * @param aggregate the aggregate root to save
     */
    void save(T aggregate);

    /**
     * Deletes an aggregate root.
     *
     * @param id the aggregate root id to delete
     */
    void delete(ID id);
}
