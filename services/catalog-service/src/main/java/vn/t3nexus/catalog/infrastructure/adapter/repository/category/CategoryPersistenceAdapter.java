package vn.t3nexus.catalog.infrastructure.adapter.repository.category;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.catalog.infrastructure.persistence.category.*;
import vn.t3nexus.catalog.infrastructure.persistence.product.ProductJpaRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CategoryPersistenceAdapter implements CategoryRepository {

    private final CategoryJpaRepository jpaRepository;
    private final CategoryClosureJpaRepository closureRepository;
    private final CategoryAttributeAssignmentJpaRepository assignmentRepository;
    private final ProductJpaRepository productJpaRepository;

    @Override
    public Optional<Category> findById(CategoryId id) {
        return jpaRepository.findById(id.getValue()).map(entity -> {
            List<CategoryAttributeAssignmentJpaEntity> assignments =
                    assignmentRepository.findByCategoryId(entity.getId());
            return CategoryMapper.toDomain(entity, assignments);
        });
    }

    @Override
    public boolean existsBySlug(String slug) {
        return jpaRepository.existsBySlug(slug);
    }

    @Override
    public boolean existsByParentId(CategoryId parentId) {
        return jpaRepository.existsByParentId(parentId.getValue());
    }

    @Override
    public boolean hasProductReference(CategoryId categoryId) {
        return productJpaRepository.existsByCategoryId(categoryId.getValue());
    }

    @Override
    @Transactional
    public void save(Category category) {
        String id = category.getId().getValue();
        boolean isNew = !jpaRepository.existsById(id);

        jpaRepository.save(CategoryMapper.toJpaEntity(category));

        // Sync assignments: delete existing + reinsert current state
        assignmentRepository.deleteByCategoryId(id);
        List<CategoryAttributeAssignmentJpaEntity> assignmentEntities =
                CategoryMapper.toAssignmentEntities(category);
        if (!assignmentEntities.isEmpty()) {
            assignmentRepository.saveAll(assignmentEntities);
        }

        if (isNew) {
            closureRepository.insertSelfReference(id);
            if (category.getParentId() != null) {
                closureRepository.insertAncestorReferences(id, category.getParentId().getValue());
            }
        }
    }

    @Override
    @Transactional
    public void delete(CategoryId id) {
        String rawId = id.getValue();
        closureRepository.deleteByDescendantId(rawId);
        assignmentRepository.deleteByCategoryId(rawId);
        jpaRepository.deleteById(rawId);
    }

    @Override
    public List<Category> findAll() {
        List<CategoryJpaEntity> entities = jpaRepository.findAll();

        // Batch load all assignments then group — avoids N+1
        Map<String, List<CategoryAttributeAssignmentJpaEntity>> assignmentsByCategory =
                assignmentRepository.findAll().stream()
                        .collect(Collectors.groupingBy(
                                CategoryAttributeAssignmentJpaEntity::getCategoryId));

        return entities.stream()
                .map(entity -> CategoryMapper.toDomain(
                        entity,
                        assignmentsByCategory.getOrDefault(entity.getId(), List.of())))
                .toList();
    }
}
