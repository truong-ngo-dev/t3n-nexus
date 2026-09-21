package vn.t3nexus.catalog.application.category;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateStatus;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryAttributeAssignment;
import vn.t3nexus.catalog.domain.category.CategoryErrorCode;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheNames;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetCategoryAttributes
        implements QueryHandler<GetCategoryAttributes.Query, GetCategoryAttributes.Result> {

    private final CategoryRepository categoryRepository;
    private final AttributeTemplateRepository attributeTemplateRepository;

    @Override
    @Cacheable(value = CacheNames.CATEGORY_ATTRIBUTES, key = "#query.categoryId()")
    public Result handle(Query query) {
        Category category = categoryRepository.findById(CategoryId.of(query.categoryId()))
                .orElseThrow(() -> new DomainException(CategoryErrorCode.CATEGORY_NOT_FOUND));

        List<CategoryAttributeAssignment> assignments = category.getAssignments();

        List<AttributeTemplateId> templateIds = assignments.stream()
                .map(CategoryAttributeAssignment::getAttributeTemplateId)
                .toList();
        Map<String, AttributeTemplate> templatesById = attributeTemplateRepository.findAllByIds(templateIds).stream()
                .collect(Collectors.toMap(t -> t.getId().getValue(), t -> t));

        // Lọc bỏ assignment trỏ tới template đã INACTIVE — Seller-facing UI không nên hiện field
        // không còn dùng được nữa (Product submit cho template inactive sẽ bị TEMPLATE_INACTIVE).
        List<Attribute> attributes = assignments.stream()
                .filter(a -> templatesById.get(a.getAttributeTemplateId().getValue()).getStatus()
                        == AttributeTemplateStatus.ACTIVE)
                .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                .map(assignment -> {
                    AttributeTemplate template = templatesById.get(assignment.getAttributeTemplateId().getValue());
                    return new Attribute(
                            template.getId().getValue(),
                            template.getName(),
                            template.getDisplayName(),
                            template.getInputType(),
                            assignment.isRequired(),
                            assignment.isFilterable(),
                            assignment.isSearchable(),
                            assignment.getDisplayOrder()
                    );
                })
                .toList();

        return new Result(attributes);
    }

    public record Query(String categoryId) {}

    public record Result(List<Attribute> attributes) {}

    public record Attribute(
            String templateId,
            String name,
            String displayName,
            InputType inputType,
            boolean required,
            boolean filterable,
            boolean searchable,
            int displayOrder
    ) {}
}
