package vn.t3nexus.catalog.domain.category;

import vn.t3nexus.catalog.domain.CatalogErrorCode;
import vn.t3nexus.catalog.domain.CatalogFieldException;
import vn.t3nexus.catalog.domain.ChangeContext;
import vn.t3nexus.catalog.domain.NameKey;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Một nút của cây phân loại ba cấp cố định (INV-CAT-01) và dòng gán mẫu thuộc tính cho lá (INV-CAT-03). Cha và cấp
 * không đổi sau khi tạo. {@code name} là tên hiển thị, đổi được; {@code nameKey} là bản chuẩn hóa tự sinh để chống trùng
 * trong cùng cha (INV-CAT-02), kiểm ở use case qua kho vì cần tra các anh em.
 * <br>Mọi hành vi ghi nhận {@link ChangeContext} và phát {@link CategoryChanged}; phiên bản trong event là phiên bản
 * SAU thay đổi ({@code version() + 1}, hoặc {@code 0} khi tạo mới).
 */
public class Category extends AbstractAggregateRoot<CategoryId> implements AggregateRoot<CategoryId> {

    public static final int NAME_MAX_LENGTH = 100;

    private String name;
    private String nameKey;
    private final CategoryId parentId;
    private final CategoryLevel level;
    private int sortOrder;
    private CategoryStatus status;
    private final List<CategoryAttributeAssignment> assignments;
    private final Long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private Category(CategoryId id, String name, String nameKey, CategoryId parentId, CategoryLevel level,
                     int sortOrder, CategoryStatus status, List<CategoryAttributeAssignment> assignments,
                     Long version, Instant createdAt, Instant updatedAt) {
        setId(id);
        this.name        = name;
        this.nameKey     = nameKey;
        this.parentId    = parentId;
        this.level       = level;
        this.sortOrder   = sortOrder;
        this.status      = status;
        this.assignments = new ArrayList<>(assignments);
        this.version     = version;
        this.createdAt   = createdAt;
        this.updatedAt   = updatedAt;
    }

    // ───────────── Factory ─────────────

    /** Khóa so trùng của tên; ném 400 nếu tên rỗng hoặc quá dài. Use case dùng để kiểm sớm tên đã có. */
    public static String nameKeyOf(String name) {
        return normalizedName(name).key();
    }

    /** {@code sortOrder}: vị trí giữa các anh em cùng cha, do use case tính (xếp cuối). */
    public static Category createRoot(CategoryId id, String name, int sortOrder, ChangeContext ctx) {
        return create(id, name, null, CategoryLevel.L1, sortOrder, ctx);
    }

    /** Cha đang đóng vẫn tạo con được: chuẩn bị trước cả nhánh rồi mở sau. Cha đã là cấp ba thì từ chối. */
    public static Category createChild(CategoryId id, String name, CategoryId parentId, CategoryLevel parentLevel,
                                       int sortOrder, ChangeContext ctx) {
        return create(id, name, parentId, parentLevel.nextLevel(), sortOrder, ctx);
    }

    private static Category create(CategoryId id, String name, CategoryId parentId, CategoryLevel level,
                                   int sortOrder, ChangeContext ctx) {
        NameKey normalized = normalizedName(name);
        Category category = new Category(id, normalized.display(), normalized.key(), parentId, level, sortOrder,
                CategoryStatus.ACTIVE, List.of(), null, ctx.now(), ctx.now());
        category.emit(ctx, CategoryChanged.Change.CREATED, category.name, null, uuidOf(parentId), level.getValue(), null);
        return category;
    }

    public static Category reconstitute(CategoryId id, String name, String nameKey, CategoryId parentId,
                                        CategoryLevel level, int sortOrder, CategoryStatus status,
                                        List<CategoryAttributeAssignment> assignments, Long version,
                                        Instant createdAt, Instant updatedAt) {
        return new Category(id, name, nameKey, parentId, level, sortOrder, status, assignments, version,
                createdAt, updatedAt);
    }

    // ───────────── Hành vi ─────────────

    /** Đổi tên hiển thị. Tên trùng trong cùng cha do use case kiểm trước (cần các anh em). */
    public void rename(String newName, ChangeContext ctx) {
        NameKey normalized = normalizedName(newName);
        String oldName = this.name;
        this.name      = normalized.display();
        this.nameKey   = normalized.key();
        this.updatedAt = ctx.now();
        emit(ctx, CategoryChanged.Change.RENAMED, this.name, oldName, null, null, null);
    }

    /** Đổi vị trí giữa các anh em cùng cha. Use case đã kiểm danh sách đủ và đúng mọi anh em. */
    public void reorder(int newSortOrder, ChangeContext ctx) {
        this.sortOrder = newSortOrder;
        this.updatedAt = ctx.now();
        emit(ctx, CategoryChanged.Change.REORDERED, null, null, uuidOf(parentId), null, null);
    }

    /** Đóng chỉ ẩn khỏi cây và chặn sản phẩm mới; không cascade xuống con (con mất đường vào vì cha bị ẩn). */
    public void deactivate(ChangeContext ctx) {
        if (status == CategoryStatus.INACTIVE) {
            throw new DomainException(CatalogErrorCode.CATALOG_INVALID_STATE);
        }
        this.status    = CategoryStatus.INACTIVE;
        this.updatedAt = ctx.now();
        emit(ctx, CategoryChanged.Change.DEACTIVATED, null, null, null, null, null);
    }

    public void activate(ChangeContext ctx) {
        if (status == CategoryStatus.ACTIVE) {
            throw new DomainException(CatalogErrorCode.CATALOG_INVALID_STATE);
        }
        this.status    = CategoryStatus.ACTIVE;
        this.updatedAt = ctx.now();
        emit(ctx, CategoryChanged.Change.ACTIVATED, null, null, null, null, null);
    }

    /**
     * Thay toàn bộ danh sách gán một lần (INV-CAT-03). Chỉ danh mục lá; mỗi mẫu đúng một lần. Việc mẫu tồn tại, mẫu mới
     * thêm dùng được và ràng buộc hợp kiểu nhập thuộc use case, vì cần đọc mẫu.
     */
    public void replaceAssignments(List<CategoryAttributeAssignment> newAssignments, ChangeContext ctx) {
        if (!level.isLeaf()) {
            throw new DomainException(CategoryErrorCode.CATEGORY_NOT_LEAF);
        }
        long distinct = newAssignments.stream()
                .map(CategoryAttributeAssignment::getAttributeTemplateId)
                .distinct()
                .count();
        if (distinct != newAssignments.size()) {
            throw new DomainException(CategoryErrorCode.ATTRIBUTE_ASSIGNMENT_DUPLICATED);
        }
        assignments.clear();
        assignments.addAll(newAssignments);
        this.updatedAt = ctx.now();
        emit(ctx, CategoryChanged.Change.ASSIGNMENTS_REPLACED, null, null, null, null, assignments.size());
    }

    /**
     * Đánh dấu sắp xóa: phát event kèm tên và cha vì sau khi xóa không còn chỗ tra. Use case kiểm không còn con rồi mới
     * xóa khỏi kho.
     */
    public void markDeleted(ChangeContext ctx) {
        emit(ctx, CategoryChanged.Change.DELETED, name, null, uuidOf(parentId), null, null);
    }

    public boolean isActive() {
        return status == CategoryStatus.ACTIVE;
    }

    public boolean isLeaf() {
        return level.isLeaf();
    }

    /** Phiên bản SAU thay đổi sắp lưu: {@code 0} khi chưa lưu, ngược lại {@code version() + 1}. */
    public long nextVersion() {
        return version == null ? 0L : version + 1;
    }

    // ───────────── Nội bộ ─────────────

    private void emit(ChangeContext ctx, CategoryChanged.Change change, String name, String oldName, UUID parent,
                      Integer levelValue, Integer count) {
        addDomainEvent(new CategoryChanged(ctx.eventId(), ctx.now(), getId(), nextVersion(), change,
                ctx.actor().accountId(), name, oldName, parent, levelValue, count));
    }

    private static UUID uuidOf(CategoryId id) {
        return id == null ? null : id.getValue();
    }

    private static NameKey normalizedName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CatalogFieldException("name");
        }
        NameKey normalized = NameKey.of(raw);
        if (normalized.display().length() > NAME_MAX_LENGTH) {
            throw new CatalogFieldException("name");
        }
        return normalized;
    }

    // ───────────── Getters ─────────────

    public String getName()              { return name; }
    public String getNameKey()           { return nameKey; }
    public CategoryId getParentId()      { return parentId; }
    public CategoryLevel getLevel()      { return level; }
    public int getSortOrder()            { return sortOrder; }
    public CategoryStatus getStatus()    { return status; }
    public Long version()                { return version; }
    public Instant getCreatedAt()        { return createdAt; }
    public Instant getUpdatedAt()        { return updatedAt; }

    public List<CategoryAttributeAssignment> getAssignments() {
        return Collections.unmodifiableList(assignments);
    }
}
