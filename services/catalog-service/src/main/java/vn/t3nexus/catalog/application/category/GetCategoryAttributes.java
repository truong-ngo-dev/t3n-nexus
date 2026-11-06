package vn.t3nexus.catalog.application.category;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOption;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.catalog.domain.category.AttributeConstraints;
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

/**
 * Thuộc tính người bán dùng ở một danh mục lá (RM-CAT-02), cho mọi người đã đăng nhập: chỉ mẫu dùng được (đang dùng, và
 * với kiểu chọn còn ít nhất một tùy chọn đang dùng), chỉ tùy chọn đang dùng theo thứ tự chuẩn. Danh mục không phải lá
 * không có dòng gán nên trả danh sách rỗng.
 */
@Service
@RequiredArgsConstructor
public class GetCategoryAttributes implements QueryHandler<GetCategoryAttributes.Query, List<GetCategoryAttributes.Attribute>> {

    private final CategoryRepository categoryRepository;
    private final AttributeTemplateRepository templateRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Attribute> handle(Query query) {
        Category category = categoryRepository.findById(CategoryId.of(query.categoryId()))
                .orElseThrow(() -> new DomainException(CategoryErrorCode.CATEGORY_NOT_FOUND));
        List<CategoryAttributeAssignment> assignments = category.getAssignments();
        if (assignments.isEmpty()) {
            return List.of();
        }

        List<AttributeTemplateId> templateIds = assignments.stream()
                .map(CategoryAttributeAssignment::getAttributeTemplateId)
                .toList();
        Map<UUID, AttributeTemplate> templates = templateRepository.findAllByIds(templateIds).stream()
                .collect(Collectors.toMap(t -> t.getId().getValue(), Function.identity()));

        return assignments.stream()
                .filter(a -> {
                    AttributeTemplate t = templates.get(a.getAttributeTemplateId().getValue());
                    return t != null && t.isUsable();
                })
                .sorted(Comparator.comparingInt(CategoryAttributeAssignment::getDisplayOrder))
                .map(a -> attribute(a, templates.get(a.getAttributeTemplateId().getValue())))
                .toList();
    }

    private static Attribute attribute(CategoryAttributeAssignment assignment, AttributeTemplate template) {
        List<Option> options = template.getOptionsInOrder().stream()
                .filter(AttributeOption::isActive)
                .map(o -> new Option(o.getId().getValue(), o.getValue(), o.getDisplayValue()))
                .toList();
        return new Attribute(template.getId().getValue(), template.getName(), template.getDisplayName(),
                template.getHint(), template.getInputType(), template.getUnit(), assignment.isRequired(),
                assignment.getConstraints(), assignment.getDisplayOrder(), options);
    }

    public record Query(UUID categoryId) {}

    public record Attribute(UUID templateId, String name, String displayName, String hint, InputType inputType,
                            String unit, boolean required, AttributeConstraints constraints, int displayOrder,
                            List<Option> options) {}

    public record Option(UUID id, String value, String displayValue) {}
}
