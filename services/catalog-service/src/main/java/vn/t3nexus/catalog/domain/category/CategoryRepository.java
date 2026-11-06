package vn.t3nexus.catalog.domain.category;

import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.List;

public interface CategoryRepository extends Repository<Category, CategoryId> {

    boolean existsByParentId(CategoryId parentId);

    /**
     * Tên đã dùng trong cùng cha (so theo khóa tên); {@code parentId} null là cấp một. Kiểm sớm để trả lỗi rõ; ràng
     * buộc duy nhất của cơ sở dữ liệu là chốt cuối (ADR-0011).
     */
    boolean existsByParentAndNameKey(CategoryId parentId, String nameKey);

    /** Như {@link #existsByParentAndNameKey} nhưng bỏ qua chính danh mục đang đổi tên. */
    boolean existsByParentAndNameKeyExcludingId(CategoryId parentId, String nameKey, CategoryId excludingId);

    /** Có tổ tiên (không tính bản thân) đang ngừng dùng không: "dùng được" = chính nó và mọi tổ tiên đang dùng. */
    boolean hasInactiveAncestor(CategoryId id);

    /** Mọi anh em cùng cha ({@code parentId} null = cấp một), theo {@code sortOrder}. */
    List<Category> findSiblings(CategoryId parentId);

    /** Toàn bộ cây, một lần truy vấn; dùng dựng cây đọc. */
    List<Category> findAll();
}
