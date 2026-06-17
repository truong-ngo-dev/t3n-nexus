package vn.t3nexus.catalog.infrastructure.persistence.category;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryJpaRepository extends JpaRepository<CategoryJpaEntity, String> {

    boolean existsBySlug(String slug);

    boolean existsByParentId(String parentId);
}
