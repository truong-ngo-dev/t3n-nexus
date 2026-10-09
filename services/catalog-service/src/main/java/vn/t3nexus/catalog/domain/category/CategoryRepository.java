package vn.t3nexus.catalog.domain.category;

import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.List;

public interface CategoryRepository extends Repository<Category, CategoryId> {


    boolean existsByParentId(CategoryId parentId);

    boolean hasProductReference(CategoryId categoryId);

    /** {@code parentId} null nghĩa là root (L1) — so trùng tên trong đúng phạm vi anh em, không toàn sàn. */
    boolean existsByParentAndNameIgnoreCase(CategoryId parentId, String name);

    boolean existsByParentAndNameIgnoreCaseExcludingId(CategoryId parentId, String name, CategoryId excludingId);

    /** Có tổ tiên (không tính bản thân) đang INACTIVE không — "dùng được" = ACTIVE + mọi tổ tiên ACTIVE (AGG-CAT-03). */
    boolean hasInactiveAncestor(CategoryId id);

    /** Mọi anh em cùng cha ({@code parentId} null = root), theo {@code sortOrder}. Dùng tính sortOrder mới và validate reorder. */
    List<Category> findSiblings(CategoryId parentId);

    List<Category> findAll();
}
