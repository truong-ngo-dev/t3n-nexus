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
    private AttributeTemplateStatus status;
    private final List<AttributeOption> options;
    private final Instant createdAt;
    private Instant updatedAt;

    private AttributeTemplate(AttributeTemplateId id, String name, String displayName,
                               InputType inputType, AttributeTemplateStatus status,
                               List<AttributeOption> options, Instant createdAt, Instant updatedAt) {
        setId(id);
        this.name        = name;
        this.displayName = displayName;
        this.inputType   = inputType;
        this.status      = status;
        this.options     = new ArrayList<>(options);
        this.createdAt   = createdAt;
        this.updatedAt   = updatedAt;
    }

    // ───────────── Factory Methods ─────────────

    public static AttributeTemplate create(AttributeTemplateId id, String name, String displayName,
                                           InputType inputType) {
        Instant now = Instant.now();
        return new AttributeTemplate(id, name, displayName, inputType,
                AttributeTemplateStatus.ACTIVE, List.of(), now, now);
    }

    public static AttributeTemplate reconstitute(AttributeTemplateId id, String name, String displayName,
                                                  InputType inputType, AttributeTemplateStatus status,
                                                  List<AttributeOption> options,
                                                  Instant createdAt, Instant updatedAt) {
        return new AttributeTemplate(id, name, displayName, inputType, status, options, createdAt, updatedAt);
    }

    // ───────────── Behaviour ─────────────

    public void updateDisplayName(String displayName) {
        this.displayName = displayName;
        this.updatedAt   = Instant.now();
    }

    // Guard (không được required bởi category nào) nằm ở AttributeTemplateDomainService — cần
    // Repository để check cross-aggregate, không thể đặt trong aggregate method.
    public void deactivate() {
        this.status    = AttributeTemplateStatus.INACTIVE;
        this.updatedAt = Instant.now();
    }

    // Đối xứng deactivate() — không cần guard, bật lại không vi phạm invariant nào.
    public void activate() {
        this.status    = AttributeTemplateStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void addOption(AttributeOptionId optionId, String value, String displayValue) {
        guardActive();
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

    // Đối xứng deactivateOption() — không cần guard, bật lại 1 option không vi phạm invariant nào
    // (tránh phân mảnh identity nếu Admin tạo option mới thay vì bật lại option cũ, xem service.md).
    public void activateOption(AttributeOptionId optionId) {
        findOption(optionId).activate();
        this.updatedAt = Instant.now();
    }

    public void updateOptionDisplayValue(AttributeOptionId optionId, String displayValue) {
        guardActive();
        findOption(optionId).updateDisplayValue(displayValue);
        this.updatedAt = Instant.now();
    }

    // Template đã deactivate thì không còn ý nghĩa quản lý thêm option của nó nữa.
    private void guardActive() {
        if (status == AttributeTemplateStatus.INACTIVE) {
            throw new DomainException(AttributeTemplateErrorCode.TEMPLATE_INACTIVE);
        }
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
    public AttributeTemplateStatus getStatus() { return status; }
    public List<AttributeOption> getOptions() { return Collections.unmodifiableList(options); }
    public Instant getCreatedAt()        { return createdAt; }
    public Instant getUpdatedAt()        { return updatedAt; }
}
