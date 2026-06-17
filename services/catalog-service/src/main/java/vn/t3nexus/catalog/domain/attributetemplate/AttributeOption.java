package vn.t3nexus.catalog.domain.attributetemplate;

import vn.t3nexus.lib.common.domain.model.AbstractEntity;
import vn.t3nexus.lib.common.domain.model.Entity;

import java.time.Instant;

public class AttributeOption extends AbstractEntity<AttributeOptionId> implements Entity<AttributeOptionId> {

    private final String value;
    private String displayValue;
    private AttributeOptionStatus status;
    private final Instant createdAt;

    private AttributeOption(AttributeOptionId id, String value, String displayValue,
                            AttributeOptionStatus status, Instant createdAt) {
        setId(id);
        this.value        = value;
        this.displayValue = displayValue;
        this.status       = status;
        this.createdAt    = createdAt;
    }

    // ───────────── Factory Methods ─────────────

    public static AttributeOption create(AttributeOptionId id, String value, String displayValue) {
        return new AttributeOption(id, value, displayValue, AttributeOptionStatus.ACTIVE, Instant.now());
    }

    public static AttributeOption reconstitute(AttributeOptionId id, String value, String displayValue,
                                               AttributeOptionStatus status, Instant createdAt) {
        return new AttributeOption(id, value, displayValue, status, createdAt);
    }

    // ───────────── Behaviour ─────────────

    void deactivate() {
        this.status = AttributeOptionStatus.INACTIVE;
    }

    void updateDisplayValue(String displayValue) {
        this.displayValue = displayValue;
    }

    // ───────────── Getters ─────────────

    public String getValue()                 { return value; }
    public String getDisplayValue()          { return displayValue; }
    public AttributeOptionStatus getStatus() { return status; }
    public Instant getCreatedAt()            { return createdAt; }
}
