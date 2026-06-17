package vn.t3nexus.catalog.domain.category;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Category extends AbstractAggregateRoot<CategoryId> implements AggregateRoot<CategoryId> {

    private String name;
    private final String slug;
    private final CategoryId parentId;
    private final CategoryLevel level;
    private String imageUrl;
    private CategoryStatus status;
    private final List<CategoryAttributeAssignment> assignments;
    private final Instant createdAt;
    private Instant updatedAt;

    private Category(CategoryId id, String name, String slug, CategoryId parentId, CategoryLevel level,
                     String imageUrl, CategoryStatus status,
                     List<CategoryAttributeAssignment> assignments,
                     Instant createdAt, Instant updatedAt) {
        setId(id);
        this.name        = name;
        this.slug        = slug;
        this.parentId    = parentId;
        this.level       = level;
        this.imageUrl    = imageUrl;
        this.status      = status;
        this.assignments = new ArrayList<>(assignments);
        this.createdAt   = createdAt;
        this.updatedAt   = updatedAt;
    }

    // ───────────── Factory Methods ─────────────

    public static Category createRoot(CategoryId id, String name, String slug) {
        Instant now = Instant.now();
        return new Category(id, name, slug, null, CategoryLevel.L1, null,
                CategoryStatus.ACTIVE, List.of(), now, now);
    }

    public static Category createChild(CategoryId id, String name, String slug,
                                       CategoryId parentId, CategoryLevel parentLevel) {
        Instant now = Instant.now();
        CategoryLevel childLevel = parentLevel.nextLevel(); // throws MAX_DEPTH_EXCEEDED if parentLevel=L3
        return new Category(id, name, slug, parentId, childLevel, null,
                CategoryStatus.ACTIVE, List.of(), now, now);
    }

    public static Category reconstitute(CategoryId id, String name, String slug,
                                        CategoryId parentId, CategoryLevel level,
                                        String imageUrl, CategoryStatus status,
                                        List<CategoryAttributeAssignment> assignments,
                                        Instant createdAt, Instant updatedAt) {
        return new Category(id, name, slug, parentId, level, imageUrl, status,
                assignments, createdAt, updatedAt);
    }

    // ───────────── Behaviour ─────────────

    public void update(String name, String imageUrl) {
        this.name      = name;
        this.imageUrl  = imageUrl;
        this.updatedAt = Instant.now();
        addDomainEvent(new CategoryUpdatedEvent(getId().getValue()));
    }

    public void assignAttribute(CategoryAttributeAssignment assignment) {
        boolean duplicate = assignments.stream()
                .anyMatch(a -> a.getAttributeTemplateId().equals(assignment.getAttributeTemplateId()));
        if (duplicate) {
            throw new DomainException(CategoryErrorCode.ASSIGNMENT_ALREADY_EXISTS);
        }
        assignments.add(assignment);
        this.updatedAt = Instant.now();
    }

    public void updateAssignment(CategoryAttributeAssignment updated) {
        AttributeTemplateId templateId = updated.getAttributeTemplateId();
        int index = findAssignmentIndex(templateId);
        assignments.set(index, updated);
        this.updatedAt = Instant.now();
    }

    public void removeAssignment(AttributeTemplateId templateId) {
        int index = findAssignmentIndex(templateId);
        assignments.remove(index);
        this.updatedAt = Instant.now();
    }

    // ───────────── Private helpers ─────────────

    private int findAssignmentIndex(AttributeTemplateId templateId) {
        for (int i = 0; i < assignments.size(); i++) {
            if (assignments.get(i).getAttributeTemplateId().equals(templateId)) {
                return i;
            }
        }
        throw new DomainException(CategoryErrorCode.ASSIGNMENT_NOT_FOUND);
    }

    // ───────────── Getters ─────────────

    public String getName()             { return name; }
    public String getSlug()             { return slug; }
    public CategoryId getParentId()     { return parentId; }
    public CategoryLevel getLevel()     { return level; }
    public String getImageUrl()         { return imageUrl; }
    public CategoryStatus getStatus()   { return status; }
    public Instant getCreatedAt()       { return createdAt; }
    public Instant getUpdatedAt()       { return updatedAt; }

    public List<CategoryAttributeAssignment> getAssignments() {
        return Collections.unmodifiableList(assignments);
    }
}
