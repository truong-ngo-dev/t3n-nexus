package vn.t3nexus.catalog.domain.category;

import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.List;

public interface CategoryRepository extends Repository<Category, CategoryId> {

    boolean existsBySlug(String slug);

    boolean existsByParentId(CategoryId parentId);

    boolean hasProductReference(CategoryId categoryId);

    List<Category> findAll();
}
