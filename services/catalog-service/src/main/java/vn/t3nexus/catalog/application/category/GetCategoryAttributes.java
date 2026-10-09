package vn.t3nexus.catalog.application.category;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOption;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.catalog.domain.category.AttributeConstraints;
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

/** Dùng cho form seller/buyer — chỉ template DÙNG ĐƯỢC, kèm {@code constraints} (ràng buộc phải nhập theo).
 *  KHÔNG kèm {@code discovery} (thuần cấu hình tìm kiếm — search-service đọc qua snapshot, không qua đây). */
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

        // Chỉ template DÙNG ĐƯỢC (ACTIVE, và SELECT còn ≥ 1 option ACTIVE) — seller không thấy thứ họ không được chọn.
        // Option: chỉ ACTIVE, theo thứ tự chuẩn.
        List<Attribute> attributes = assignments.stream()
                .filter(a -> {
                    AttributeTemplate t = templatesById.get(a.getAttributeTemplateId().getValue());
                    return t != null && t.isUsable();
                })
                .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                .map(assignment -> {
                    AttributeTemplate template = templatesById.get(assignment.getAttributeTemplateId().getValue());
                    return new Attribute(
                            template.getId().getValue(),
                            template.getName(),
                            template.getDisplayName(),
                            template.getHint(),
                            template.getInputType(),
                            template.getUnit(),
                            assignment.isRequired(),
                            assignment.getConstraints(),
                            assignment.getDisplayOrder(),
                            template.getOptionsInOrder().stream()
                                    .filter(AttributeOption::isActive)
                                    .map(o -> new Option(o.getId().getValue(), o.getValue(), o.getDisplayValue()))
                                    .toList()
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
            String hint,
            InputType inputType,
            String unit,
            boolean required,
            AttributeConstraints constraints,
            int displayOrder,
            List<Option> options
    ) {}

    public record Option(String id, String value, String displayValue) {}
}
