package vn.t3nexus.catalog.infrastructure.persistence.attributetemplate;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateStatus;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;

import java.util.List;

public interface AttributeTemplateJpaRepository extends JpaRepository<AttributeTemplateJpaEntity, String> {

    boolean existsByName(String name);

    // CAST(:keyword AS string): tham số null không suy ra được kiểu lúc prepare → lower(bytea) (cùng lỗi đã gặp ở
    // scheduler-service ScheduledJobJpaRepository).
    @Query("""
            SELECT t FROM AttributeTemplateJpaEntity t
            WHERE (:keyword IS NULL
                   OR LOWER(t.name) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
                   OR LOWER(t.displayName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))
              AND (:inputType IS NULL OR t.inputType = :inputType)
              AND (:status IS NULL OR t.status = :status)
            ORDER BY t.displayName, t.id
            """)
    List<AttributeTemplateJpaEntity> search(@Param("keyword") String keyword,
                                            @Param("inputType") InputType inputType,
                                            @Param("status") AttributeTemplateStatus status,
                                            Pageable pageable);

    @Query("""
            SELECT COUNT(t) FROM AttributeTemplateJpaEntity t
            WHERE (:keyword IS NULL
                   OR LOWER(t.name) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
                   OR LOWER(t.displayName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))
              AND (:inputType IS NULL OR t.inputType = :inputType)
              AND (:status IS NULL OR t.status = :status)
            """)
    long countSearch(@Param("keyword") String keyword,
                     @Param("inputType") InputType inputType,
                     @Param("status") AttributeTemplateStatus status);
}
