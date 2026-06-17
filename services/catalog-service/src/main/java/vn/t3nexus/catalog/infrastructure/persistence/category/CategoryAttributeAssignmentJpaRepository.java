package vn.t3nexus.catalog.infrastructure.persistence.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface CategoryAttributeAssignmentJpaRepository
        extends JpaRepository<CategoryAttributeAssignmentJpaEntity, CategoryAssignmentKey> {

    List<CategoryAttributeAssignmentJpaEntity> findByCategoryId(String categoryId);

    @Transactional
    void deleteByCategoryId(String categoryId);
}
