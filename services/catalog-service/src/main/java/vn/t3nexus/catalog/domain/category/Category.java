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

    // Soft toggle điều hướng/hiển thị — KHÔNG phải khoá toàn vẹn dữ liệu, nên KHÔNG guard theo "đang có
    // Product/children hay không" (khác hẳn hard-delete). Khớp Magento/Shopify: deactivate ẩn khỏi
    // GetCategoryTree + chặn CreateProduct mới, nhưng Product cũ tham chiếu category này không bị ảnh
    // hưởng gì (không cascade xuống children — mỗi node tự quản lý độc lập, đúng tinh thần leaf-only
    // attribute assignment). Hard-delete (DeleteCategory, guard HAS_CHILDREN/HAS_PRODUCT_REFERENCE) vẫn
    // giữ nguyên, riêng biệt — chỉ dùng dọn category tạo nhầm, chưa từng ai dùng.
    public void deactivate() {
        this.status    = CategoryStatus.INACTIVE;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        this.status    = CategoryStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    // Thay toàn bộ danh sách attribute — assign/update/remove 1 attribute đều quy về gọi lại method
    // này với danh sách mong muốn cuối cùng (client tự GET rồi sửa trước khi PUT lại), khớp đúng cách
    // persistence đã làm (xoá hết + insert lại toàn bộ mỗi lần save).
    public void replaceAssignments(List<CategoryAttributeAssignment> newAssignments) {
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
