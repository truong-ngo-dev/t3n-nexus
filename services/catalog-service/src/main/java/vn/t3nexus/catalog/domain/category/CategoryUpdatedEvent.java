package vn.t3nexus.catalog.domain.category;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;

import java.time.Instant;
import java.util.UUID;

public class CategoryUpdatedEvent extends AbstractDomainEvent {

    public CategoryUpdatedEvent(String categoryId) {
        super(UUID.randomUUID().toString(), Instant.now(), categoryId, "Category");
    }

    @Override
    public String getRoutingKey() { return "catalog.category.updated"; }

    @Override
    public Object getPayload() { return new Payload(getAggregateId()); }

    public String getCategoryId() { return getAggregateId(); }

    public record Payload(String categoryId) {}
}
