package vn.t3nexus.catalog.infrastructure.persistence.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface CategoryClosureJpaRepository
        extends JpaRepository<CategoryClosureJpaEntity, CategoryClosureKey> {

    List<CategoryClosureJpaEntity> findByAncestorId(String ancestorId);

    List<CategoryClosureJpaEntity> findByDescendantId(String descendantId);

    long countByAncestorIdAndDepth(String ancestorId, int depth);

    @Transactional
    void deleteByDescendantId(String descendantId);

    @Transactional
    @Modifying
    @Query(value = "INSERT INTO category_closure (ancestor_id, descendant_id, depth) VALUES (:id, :id, 0)",
            nativeQuery = true)
    void insertSelfReference(@Param("id") String id);

    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO category_closure (ancestor_id, descendant_id, depth)
            SELECT ancestor_id, :id, depth + 1
            FROM category_closure
            WHERE descendant_id = :parentId
            """,
            nativeQuery = true)
    void insertAncestorReferences(@Param("id") String id, @Param("parentId") String parentId);
}
