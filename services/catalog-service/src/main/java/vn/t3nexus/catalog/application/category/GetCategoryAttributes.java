package vn.t3nexus.catalog.application.category;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeScope;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryAttributeAssignment;
import vn.t3nexus.catalog.domain.category.CategoryErrorCode;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheNames;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.ArrayList;
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

        List<AttributeTemplate> globalTemplates =
                attributeTemplateRepository.findAllByScope(AttributeScope.GLOBAL);

        Map<String, AttributeTemplate> templateById = globalTemplates.stream()
                .collect(Collectors.toMap(t -> t.getId().getValue(), t -> t));

        // Load CATEGORY-scope templates referenced by this category's assignments
        for (CategoryAttributeAssignment assignment : category.getAssignments()) {
            String templateId = assignment.getAttributeTemplateId().getValue();
            if (!templateById.containsKey(templateId)) {
                attributeTemplateRepository.findById(AttributeTemplateId.of(templateId))
                        .ifPresent(t -> templateById.put(templateId, t));
            }
        }

        List<Attribute> attributes = new ArrayList<>();

        // GLOBAL templates — no assignment config, appear first (displayOrder = -1)
        for (AttributeTemplate template : globalTemplates) {
            attributes.add(new Attribute(
                    template.getId().getValue(),
                    template.getName(),
                    template.getDisplayName(),
                    template.getInputType(),
                    AttributeScope.GLOBAL,
                    false, false, false, -1
            ));
        }

        // CATEGORY templates from assignments — sorted by displayOrder
        category.getAssignments().stream()
                .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                .forEach(assignment -> {
                    AttributeTemplate template =
                            templateById.get(assignment.getAttributeTemplateId().getValue());
                    if (template != null && template.getScope() == AttributeScope.CATEGORY) {
                        attributes.add(new Attribute(
                                template.getId().getValue(),
                                template.getName(),
                                template.getDisplayName(),
                                template.getInputType(),
                                AttributeScope.CATEGORY,
                                assignment.isVariantDefining(),
                                assignment.isRequired(),
                                assignment.isFilterable(),
                                assignment.getDisplayOrder()
                        ));
                    }
                });

        return new Result(attributes);
    }

    public record Query(String categoryId) {}

    public record Result(List<Attribute> attributes) {}

    public record Attribute(
            String templateId,
            String name,
            String displayName,
            InputType inputType,
            AttributeScope scope,
            boolean variantDefining,
            boolean required,
            boolean filterable,
            int displayOrder
    ) {}
}
