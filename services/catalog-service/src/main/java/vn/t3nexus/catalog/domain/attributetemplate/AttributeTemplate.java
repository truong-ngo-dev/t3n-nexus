package vn.t3nexus.catalog.domain.attributetemplate;

import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AttributeTemplate extends AbstractAggregateRoot<AttributeTemplateId>
        implements AggregateRoot<AttributeTemplateId> {

    private final String name;
    private String displayName;
    private final InputType inputType;
    private final AttributeScope scope;
    private final List<AttributeOption> options;
    private final Instant createdAt;
    private Instant updatedAt;

    private AttributeTemplate(AttributeTemplateId id, String name, String displayName,
                               InputType inputType, AttributeScope scope,
                               List<AttributeOption> options, Instant createdAt, Instant updatedAt) {
        setId(id);
        this.name        = name;
        this.displayName = displayName;
        this.inputType   = inputType;
        this.scope       = scope;
        this.options     = new ArrayList<>(options);
        this.createdAt   = createdAt;
        this.updatedAt   = updatedAt;
    }

    // ───────────── Factory Methods ─────────────

    public static AttributeTemplate create(AttributeTemplateId id, String name, String displayName,
                                           InputType inputType, AttributeScope scope) {
        Instant now = Instant.now();
        return new AttributeTemplate(id, name, displayName, inputType, scope, List.of(), now, now);
    }

    public static AttributeTemplate reconstitute(AttributeTemplateId id, String name, String displayName,
                                                  InputType inputType, AttributeScope scope,
                                                  List<AttributeOption> options,
                                                  Instant createdAt, Instant updatedAt) {
        return new AttributeTemplate(id, name, displayName, inputType, scope, options, createdAt, updatedAt);
    }

    // ───────────── Behaviour ─────────────

    public void updateDisplayName(String displayName) {
        this.displayName = displayName;
        this.updatedAt   = Instant.now();
    }

    public void addOption(AttributeOptionId optionId, String value, String displayValue) {
        if (inputType != InputType.SELECT) {
            throw new DomainException(AttributeTemplateErrorCode.INPUT_TYPE_NOT_SELECT);
        }
        options.add(AttributeOption.create(optionId, value, displayValue));
        this.updatedAt = Instant.now();
    }

    public void deactivateOption(AttributeOptionId optionId) {
        findOption(optionId).deactivate();
        this.updatedAt = Instant.now();
    }

    public void updateOptionDisplayValue(AttributeOptionId optionId, String displayValue) {
        findOption(optionId).updateDisplayValue(displayValue);
        this.updatedAt = Instant.now();
    }

    private AttributeOption findOption(AttributeOptionId optionId) {
        return options.stream()
                .filter(o -> o.getId().equals(optionId))
                .findFirst()
                .orElseThrow(() -> new DomainException(AttributeTemplateErrorCode.OPTION_NOT_FOUND));
    }

    // ───────────── Getters ─────────────

    public String getName()              { return name; }
    public String getDisplayName()       { return displayName; }
    public InputType getInputType()      { return inputType; }
    public AttributeScope getScope()     { return scope; }
    public List<AttributeOption> getOptions() { return Collections.unmodifiableList(options); }
    public Instant getCreatedAt()        { return createdAt; }
    public Instant getUpdatedAt()        { return updatedAt; }
}
