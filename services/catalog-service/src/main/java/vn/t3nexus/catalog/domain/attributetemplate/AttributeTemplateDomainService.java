package vn.t3nexus.catalog.domain.attributetemplate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryAttributeAssignment;
import vn.t3nexus.catalog.domain.category.CategoryErrorCode;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.catalog.domain.product.ProductAttributeValue;
import vn.t3nexus.catalog.domain.product.ProductErrorCode;
import vn.t3nexus.catalog.domain.variant.VariantRepository;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttributeTemplateDomainService {

    private final VariantRepository variantRepository;
    private final CategoryRepository categoryRepository;
    private final AttributeTemplateRepository attributeTemplateRepository;

    public void validateOptionNotUsedByVariant(AttributeOptionId optionId) {
        if (variantRepository.existsByOptionId(optionId)) {
            throw new DomainException(AttributeTemplateErrorCode.OPTION_IN_USE);
        }
    }

    public void validateProductAttributes(CategoryId categoryId, List<ProductAttributeValue> submitted) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new DomainException(CategoryErrorCode.CATEGORY_NOT_FOUND));

        Set<String> globalIds = attributeTemplateRepository.findAllByScope(AttributeScope.GLOBAL)
                .stream()
                .map(t -> t.getId().getValue())
                .collect(Collectors.toSet());

        List<CategoryAttributeAssignment> assignments = category.getAssignments();

        Set<String> assignedIds = assignments.stream()
                .map(a -> a.getAttributeTemplateId().getValue())
                .collect(Collectors.toSet());

        Set<String> allowedIds = new java.util.HashSet<>(globalIds);
        allowedIds.addAll(assignedIds);

        Set<String> submittedIds = submitted.stream()
                .map(v -> v.templateId().getValue())
                .collect(Collectors.toSet());

        // Check không có templateId lạ
        for (String templateId : submittedIds) {
            if (!allowedIds.contains(templateId)) {
                throw new DomainException(ProductErrorCode.ATTRIBUTE_NOT_IN_CATEGORY);
            }
        }

        // Check required templates phải có giá trị
        for (CategoryAttributeAssignment assignment : assignments) {
            if (assignment.isRequired()) {
                String templateId = assignment.getAttributeTemplateId().getValue();
                boolean hasValue = submitted.stream()
                        .anyMatch(v -> v.templateId().getValue().equals(templateId)
                                && v.value() != null && !v.value().isBlank());
                if (!hasValue) {
                    throw new DomainException(ProductErrorCode.REQUIRED_ATTRIBUTE_MISSING);
                }
            }
        }
    }
}
