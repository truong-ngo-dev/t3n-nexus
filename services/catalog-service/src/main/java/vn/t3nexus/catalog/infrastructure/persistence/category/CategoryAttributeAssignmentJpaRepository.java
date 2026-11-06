package vn.t3nexus.catalog.infrastructure.persistence.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CategoryAttributeAssignmentJpaRepository
        extends JpaRepository<CategoryAttributeAssignmentJpaEntity, CategoryAssignmentKey> {

    List<CategoryAttributeAssignmentJpaEntity> findByCategoryId(UUID categoryId);

    // Xóa bằng một câu lệnh rồi chèn lại danh sách mới: khóa chính (category_id, template_id) nên không thể "sửa tại chỗ"
    // khi danh sách đổi thành phần.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM CategoryAttributeAssignmentJpaEntity a WHERE a.categoryId = :categoryId")
    void deleteAllByCategoryId(@Param("categoryId") UUID categoryId);
}
