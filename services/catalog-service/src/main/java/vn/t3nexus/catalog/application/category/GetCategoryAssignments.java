package vn.t3nexus.catalog.application.category;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryAttributeAssignment;
import vn.t3nexus.catalog.domain.category.CategoryErrorCode;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Danh sách thuộc tính được gán cho quản trị: đủ mọi dòng, kể cả mẫu nay đã ngừng dùng. */
@Service
@RequiredArgsConstructor
public class GetCategoryAssignments implements QueryHandler<GetCategoryAssignments.Query, List<AssignmentView>> {

    private final CategoryRepository categoryRepository;
    private final AttributeTemplateRepository templateRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentView> handle(Query query) {
        query.actor().requireAdmin();
        Category category = categoryRepository.findById(CategoryId.of(query.categoryId()))
                .orElseThrow(() -> new DomainException(CategoryErrorCode.CATEGORY_NOT_FOUND));
        List<CategoryAttributeAssignment> assignments = category.getAssignments();
        if (assignments.isEmpty()) {
            return List.of();
        }

        Map<UUID, AttributeTemplate> templates = templateRepository.findAllByIds(
                        assignments.stream().map(CategoryAttributeAssignment::getAttributeTemplateId).toList()).stream()
                .collect(Collectors.toMap(t -> t.getId().getValue(), Function.identity()));
        return assignments.stream()
                .sorted(Comparator.comparingInt(CategoryAttributeAssignment::getDisplayOrder))
                .map(a -> AssignmentView.of(a, templates.get(a.getAttributeTemplateId().getValue())))
                .toList();
    }

    public record Query(CatalogActor actor, UUID categoryId) {}
}
