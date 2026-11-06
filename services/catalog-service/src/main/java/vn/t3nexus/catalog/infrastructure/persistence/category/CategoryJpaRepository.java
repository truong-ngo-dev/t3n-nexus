package vn.t3nexus.catalog.infrastructure.persistence.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CategoryJpaRepository extends JpaRepository<CategoryJpaEntity, UUID> {

    boolean existsByParentId(UUID parentId);

    boolean existsByParentIdAndNameKey(UUID parentId, String nameKey);

    boolean existsByParentIdAndNameKeyAndIdNot(UUID parentId, String nameKey, UUID id);

    boolean existsByParentIdIsNullAndNameKey(String nameKey);

    boolean existsByParentIdIsNullAndNameKeyAndIdNot(String nameKey, UUID id);

    List<CategoryJpaEntity> findByParentIdOrderBySortOrderAscIdAsc(UUID parentId);

    List<CategoryJpaEntity> findByParentIdIsNullOrderBySortOrderAscIdAsc();
}
