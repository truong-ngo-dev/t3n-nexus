package vn.t3nexus.catalog.infrastructure.adapter.repository.category;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.catalog.domain.category.CategoryStatus;
import vn.t3nexus.catalog.infrastructure.persistence.category.CategoryAttributeAssignmentJpaEntity;
import vn.t3nexus.catalog.infrastructure.persistence.category.CategoryAttributeAssignmentJpaRepository;
import vn.t3nexus.catalog.infrastructure.persistence.category.CategoryJpaEntity;
import vn.t3nexus.catalog.infrastructure.persistence.category.CategoryJpaRepository;
import vn.t3nexus.catalog.infrastructure.persistence.category.CategoryMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CategoryPersistenceAdapter implements CategoryRepository {

    private final CategoryJpaRepository jpaRepository;
    private final CategoryAttributeAssignmentJpaRepository assignmentRepository;

    @Override
    public Optional<Category> findById(CategoryId id) {
        return jpaRepository.findById(id.getValue())
                .map(entity -> CategoryMapper.toDomain(entity, assignmentRepository.findByCategoryId(entity.getId())));
    }

    @Override
    public boolean existsByParentId(CategoryId parentId) {
        return jpaRepository.existsByParentId(parentId.getValue());
    }

    @Override
    public boolean existsByParentAndNameKey(CategoryId parentId, String nameKey) {
        return parentId == null
                ? jpaRepository.existsByParentIdIsNullAndNameKey(nameKey)
                : jpaRepository.existsByParentIdAndNameKey(parentId.getValue(), nameKey);
    }

    @Override
    public boolean existsByParentAndNameKeyExcludingId(CategoryId parentId, String nameKey, CategoryId excludingId) {
        return parentId == null
                ? jpaRepository.existsByParentIdIsNullAndNameKeyAndIdNot(nameKey, excludingId.getValue())
                : jpaRepository.existsByParentIdAndNameKeyAndIdNot(parentId.getValue(), nameKey, excludingId.getValue());
    }

    /** Cây tối đa ba cấp nên tổ tiên là cha và ông: đi lên theo khóa chính, tối đa hai lần tra. */
    @Override
    public boolean hasInactiveAncestor(CategoryId id) {
        UUID current = jpaRepository.findById(id.getValue()).map(CategoryJpaEntity::getParentId).orElse(null);
        while (current != null) {
            CategoryJpaEntity ancestor = jpaRepository.findById(current).orElse(null);
            if (ancestor == null) return false;
            if (ancestor.getStatus() != CategoryStatus.ACTIVE) return true;
            current = ancestor.getParentId();
        }
        return false;
    }

    @Override
    public List<Category> findSiblings(CategoryId parentId) {
        List<CategoryJpaEntity> siblings = parentId == null
                ? jpaRepository.findByParentIdIsNullOrderBySortOrderAscIdAsc()
                : jpaRepository.findByParentIdOrderBySortOrderAscIdAsc(parentId.getValue());
        return siblings.stream()
                .map(entity -> CategoryMapper.toDomain(entity, assignmentRepository.findByCategoryId(entity.getId())))
                .toList();
    }

    @Override
    public List<Category> findAll() {
        // Tải mọi dòng gán một lần rồi nhóm theo danh mục, tránh N+1.
        Map<UUID, List<CategoryAttributeAssignmentJpaEntity>> assignmentsByCategory =
                assignmentRepository.findAll().stream()
                        .collect(Collectors.groupingBy(CategoryAttributeAssignmentJpaEntity::getCategoryId));
        return jpaRepository.findAll().stream()
                .map(entity -> CategoryMapper.toDomain(entity,
                        assignmentsByCategory.getOrDefault(entity.getId(), List.of())))
                .toList();
    }

    /**
     * Lưu dòng danh mục trước ({@code saveAndFlush}: khóa lạc quan qua {@code version} và vi phạm ràng buộc nổ ngay
     * trong giao dịch, ADR-0011), rồi thay danh sách gán. Dòng danh mục luôn đổi ({@code updated_at}) nên mọi thay đổi
     * danh sách gán đều tăng phiên bản.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(Category category) {
        jpaRepository.saveAndFlush(CategoryMapper.toJpaEntity(category));
        assignmentRepository.deleteAllByCategoryId(category.getId().getValue());
        List<CategoryAttributeAssignmentJpaEntity> assignmentEntities = CategoryMapper.toAssignmentEntities(category);
        if (!assignmentEntities.isEmpty()) {
            assignmentRepository.saveAllAndFlush(assignmentEntities);
        }
    }

    /** Dòng gán đi theo danh mục nhờ khóa ngoài ON DELETE CASCADE; con còn thì khóa ngoài cha chặn (RESTRICT). */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(CategoryId id) {
        jpaRepository.deleteById(id.getValue());
        jpaRepository.flush();
    }
}
