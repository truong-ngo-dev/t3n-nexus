package vn.t3nexus.lib.common.domain.model;

/**
 * Base class for entities that require a surrogate (technical) identity for persistence purposes,
 * separate from their domain identity.
 */
public abstract class SurrogateIdentity {
    /** technical ID for database primary key */
    private Long surrogateId;
}
