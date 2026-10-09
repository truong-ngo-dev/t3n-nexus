package vn.t3nexus.catalog.domain.category;

import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Category extends AbstractAggregateRoot<CategoryId> implements AggregateRoot<CategoryId> {

    private String name;
    private String slug;
    private final CategoryId parentId;
    private final CategoryLevel level;
    private String imageUrl;
    private int sortOrder;
    private CategoryStatus status;
    private final List<CategoryAttributeAssignment> assignments;
    private final Instant createdAt;
    private Instant updatedAt;

    private Category(CategoryId id, String name, String slug, CategoryId parentId, CategoryLevel level,
                     String imageUrl, int sortOrder, CategoryStatus status,
                     List<CategoryAttributeAssignment> assignments,
                     Instant createdAt, Instant updatedAt) {
        setId(id);
        this.name        = name;
        this.slug        = slug;
        this.parentId    = parentId;
        this.level       = level;
        this.imageUrl    = imageUrl;
        this.sortOrder   = sortOrder;
        this.status      = status;
        this.assignments = new ArrayList<>(assignments);
        this.createdAt   = createdAt;
        this.updatedAt   = updatedAt;
    }

    // ───────────── Factory Methods ─────────────

    /** {@code sortOrder}: vị trí giữa các anh em cùng cha — caller tính (VD số anh em hiện có). */
    public static Category createRoot(CategoryId id, String name, int sortOrder) {
        Instant now = Instant.now();
        Category category = new Category(id, name, CategorySlug.from(name), null, CategoryLevel.L1, null, sortOrder,
                CategoryStatus.ACTIVE, List.of(), now, now);
        category.raiseUpdated();
        return category;
    }

    /** Cha đang đóng vẫn tạo con được — chuẩn bị trước rồi mở cả nhánh sau (analysis.md AGG-CAT-03). */
    public static Category createChild(CategoryId id, String name,
                                       CategoryId parentId, CategoryLevel parentLevel, int sortOrder) {
        Instant now = Instant.now();
        CategoryLevel childLevel = parentLevel.nextLevel(); // throws MAX_DEPTH_EXCEEDED if parentLevel=L3
        Category category = new Category(id, name, CategorySlug.from(name), parentId, childLevel, null, sortOrder,
                CategoryStatus.ACTIVE, List.of(), now, now);
        category.raiseUpdated();
        return category;
    }

    public static Category reconstitute(CategoryId id, String name, String slug,
                                        CategoryId parentId, CategoryLevel level,
                                        String imageUrl, int sortOrder, CategoryStatus status,
                                        List<CategoryAttributeAssignment> assignments,
                                        Instant createdAt, Instant updatedAt) {
        return new Category(id, name, slug, parentId, level, imageUrl, sortOrder, status,
                assignments, createdAt, updatedAt);
    }

    // ───────────── Behaviour ─────────────

    /** Đổi tên thì sinh lại slug — slug chỉ để đọc, URL cũ vẫn mở được nhờ ID (service.md T-10). */
    public void update(String name, String imageUrl) {
        this.name      = name;
        this.slug      = CategorySlug.from(name);
        this.imageUrl  = imageUrl;
        this.updatedAt = Instant.now();
        raiseUpdated();
    }

    // Đóng chỉ ẩn khỏi cây duyệt + chặn sản phẩm mới — không guard theo con/sản phẩm, không cascade xuống con
    // (con tự mất đường vào vì cha bị ẩn). Khác xoá hẳn (analysis.md AGG-CAT-03).
    public void deactivate() {
        this.status    = CategoryStatus.INACTIVE;
        this.updatedAt = Instant.now();
        raiseUpdated();
    }

    public void activate() {
        this.status    = CategoryStatus.ACTIVE;
        this.updatedAt = Instant.now();
        raiseUpdated();
    }

    /** Đổi vị trí giữa các anh em cùng cha — caller (use case) đã kiểm danh sách đủ/đúng mọi anh em. */
    public void reorder(int sortOrder) {
        this.sortOrder = sortOrder;
        this.updatedAt = Instant.now();
        raiseUpdated();
    }

    /** Xoá hẳn — caller kiểm không còn con/sản phẩm (INV-CAT-035, INV-CAT-98) rồi mới xoá khỏi repository. */
    public void markDeleted() {
        raiseUpdated();
    }

    /**
     * Thay toàn bộ danh sách thuộc tính áp dụng (replace-all — service.md T-03). Chỉ danh mục lá (INV-CAT-033);
     * mỗi thuộc tính đúng 1 lần (INV-CAT-034).
     */
    public void replaceAssignments(List<CategoryAttributeAssignment> newAssignments) {
        if (level != CategoryLevel.L3) {
            throw new DomainException(CategoryErrorCode.ATTRIBUTE_ASSIGNMENT_REQUIRES_LEAF);
        }
        long distinctTemplateCount = newAssignments.stream()
                .map(CategoryAttributeAssignment::getAttributeTemplateId)
                .distinct()
                .count();
        if (distinctTemplateCount != newAssignments.size()) {
            throw new DomainException(CategoryErrorCode.ASSIGNMENT_ALREADY_EXISTS);
        }

        assignments.clear();
        assignments.addAll(newAssignments);
        this.updatedAt = Instant.now();
        raiseUpdated();
    }

    // EVT-CAT-031 — mọi thay đổi cây/luật thuộc tính đều báo cho search (cây danh mục + facet).
    private void raiseUpdated() {
        addDomainEvent(new CategoryUpdatedEvent(getId().getValue()));
    }

    // ───────────── Getters ─────────────

    public String getName()             { return name; }
    public String getSlug()             { return slug; }
    public CategoryId getParentId()     { return parentId; }
    public CategoryLevel getLevel()     { return level; }
    public String getImageUrl()         { return imageUrl; }
    public int getSortOrder()           { return sortOrder; }
    public CategoryStatus getStatus()   { return status; }
    public Instant getCreatedAt()       { return createdAt; }
    public Instant getUpdatedAt()       { return updatedAt; }

    public List<CategoryAttributeAssignment> getAssignments() {
        return Collections.unmodifiableList(assignments);
    }
}
