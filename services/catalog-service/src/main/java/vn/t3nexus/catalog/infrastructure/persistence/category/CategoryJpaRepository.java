package vn.t3nexus.catalog.infrastructure.persistence.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryJpaRepository extends JpaRepository<CategoryJpaEntity, String> {


    boolean existsByParentId(String parentId);

    List<CategoryJpaEntity> findByParentIdOrderBySortOrder(String parentId);

    List<CategoryJpaEntity> findByParentIdIsNullOrderBySortOrder();
}
