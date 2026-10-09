package vn.t3nexus.catalog.domain.attributetemplate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.category.AttributeConstraintChecker;
import vn.t3nexus.catalog.domain.category.AttributeConstraints;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryAttributeAssignment;
import vn.t3nexus.catalog.domain.category.CategoryErrorCode;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.catalog.domain.product.ProductAttributeValue;
import vn.t3nexus.catalog.domain.product.ProductErrorCode;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttributeTemplateDomainService {

    private final CategoryRepository categoryRepository;
    private final AttributeTemplateRepository attributeTemplateRepository;

    /**
     * @param previouslyPersisted bản đã lưu trước đó (rỗng khi Create — không có gì để "grandfather").
     *   Giá trị giống hệt bản cũ được miễn kiểm lại theo trạng thái/quy tắc HIỆN TẠI — tránh Seller bị kẹt khi sửa
     *   phần không liên quan chỉ vì Admin đã tắt option/template sau khi sản phẩm được tạo. Thuộc tính bắt buộc bị
     *   thiếu KHÔNG được miễn (thiếu thì không có gì để giữ). Xem analysis.md INV-CAT-92.
     */
    public void validateProductAttributes(
            CategoryId categoryId,
            List<ProductAttributeValue> submitted,
            List<ProductAttributeValue> previouslyPersisted) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new DomainException(CategoryErrorCode.CATEGORY_NOT_FOUND));

        List<CategoryAttributeAssignment> assignments = category.getAssignments();

        Set<String> allowedIds = assignments.stream()
                .map(a -> a.getAttributeTemplateId().getValue())
                .collect(Collectors.toSet());

        Map<String, List<String>> previousByTemplate = previouslyPersisted.stream()
                .collect(Collectors.toMap(v -> v.templateId().getValue(), ProductAttributeValue::values));

        Set<String> submittedIds = submitted.stream()
                .map(v -> v.templateId().getValue())
                .collect(Collectors.toSet());

        // Không có templateId lạ — Product chỉ dùng đúng thuộc tính danh mục lá đã gán (không có custom attribute).
        // Grandfather: templateId đã có ở bản cũ (danh mục gỡ assignment sau đó) thì miễn.
        for (String templateId : submittedIds) {
            if (!allowedIds.contains(templateId) && !previousByTemplate.containsKey(templateId)) {
                throw new DomainException(ProductErrorCode.ATTRIBUTE_NOT_IN_CATEGORY);
            }
        }

        // 1 round-trip cho mọi template cần: template được submit + template bắt buộc của danh mục.
        Set<AttributeTemplateId> neededIds = new LinkedHashSet<>();
        submitted.forEach(v -> neededIds.add(v.templateId()));
        assignments.stream().filter(CategoryAttributeAssignment::isRequired)
                .forEach(a -> neededIds.add(a.getAttributeTemplateId()));
        Map<String, AttributeTemplate> templatesById = attributeTemplateRepository.findAllByIds(neededIds).stream()
                .collect(Collectors.toMap(t -> t.getId().getValue(), t -> t));

        // "Bắt buộc có hiệu lực": required chỉ ép seller điền khi template đang DÙNG ĐƯỢC. Template bị tắt (hoặc SELECT
        // hết option ACTIVE) thì form không hiện field — đòi điền là để seller kẹt. Cờ required của danh mục không bị sửa.
        for (CategoryAttributeAssignment assignment : assignments) {
            if (!assignment.isRequired()) continue;
            String templateId = assignment.getAttributeTemplateId().getValue();
            AttributeTemplate template = templatesById.get(templateId);
            if (template == null || !template.isUsable()) continue;
            if (!submittedIds.contains(templateId)) {
                throw new DomainException(ProductErrorCode.REQUIRED_ATTRIBUTE_MISSING);
            }
        }

        Map<String, CategoryAttributeAssignment> assignmentByTemplate = assignments.stream()
                .collect(Collectors.toMap(a -> a.getAttributeTemplateId().getValue(), a -> a));
        LocalDate today = LocalDate.now();

        for (ProductAttributeValue submittedValue : submitted) {
            String templateId = submittedValue.templateId().getValue();
            AttributeTemplate template = templatesById.get(templateId);
            if (template == null) continue;

            // Trục phân loại: mỗi đơn vị bán được nhận ĐÚNG 1 giá trị → chỉ kiểu "chọn 1" (analysis.md AGG-CAT-04).
            if (submittedValue.isVariantDefining() && template.getInputType() != InputType.SINGLE_SELECT) {
                throw new DomainException(ProductErrorCode.VARIANT_DEFINING_REQUIRES_SELECT);
            }

            List<String> previousValues = previousByTemplate.getOrDefault(templateId, List.of());
            CategoryAttributeAssignment assignment = assignmentByTemplate.get(templateId);
            AttributeConstraints constraints = assignment == null ? AttributeConstraints.NONE : assignment.getConstraints();

            // maxSelections tính trên cả tập — chỉ kiểm khi tập đổi (tập giữ nguyên được miễn, INV-CAT-92).
            boolean setChanged = !Set.copyOf(submittedValue.values()).equals(Set.copyOf(previousValues));
            if (setChanged && template.getInputType() == InputType.MULTI_SELECT
                    && !AttributeConstraintChecker.satisfiesSelectionCount(constraints, submittedValue.values().size())) {
                throw new DomainException(ProductErrorCode.ATTRIBUTE_VALUE_CONSTRAINT_VIOLATED);
            }

            for (String value : submittedValue.values()) {
                // Grandfather ở cấp TỪNG GIÁ TRỊ.
                if (previousValues.contains(value)) continue;

                // Thuộc tính đã bị gỡ khỏi danh mục: chỉ giữ nguyên hoặc bỏ hẳn, không thêm/sửa (analysis.md AGG-CAT-04).
                if (assignment == null) {
                    throw new DomainException(ProductErrorCode.ATTRIBUTE_NOT_IN_CATEGORY);
                }

                if (template.getStatus() == AttributeTemplateStatus.INACTIVE) {
                    throw new DomainException(AttributeTemplateErrorCode.TEMPLATE_INACTIVE);
                }

                if (!template.getInputType().isSelect()) {
                    // Guard mặc định theo kiểu (TEXT/NUMBER/BOOLEAN/DATE) — do template quy định.
                    if (!template.isValidRawValue(value)) {
                        throw new DomainException(ProductErrorCode.ATTRIBUTE_VALUE_FORMAT_INVALID);
                    }
                    // Ràng buộc riêng của danh mục — chồng lên guard mặc định.
                    if (!AttributeConstraintChecker.satisfies(constraints, template.getInputType(), value, today)) {
                        throw new DomainException(ProductErrorCode.ATTRIBUTE_VALUE_CONSTRAINT_VIOLATED);
                    }
                    continue;
                }

                boolean matchesActiveOption = template.getOptions().stream()
                        .anyMatch(o -> o.isActive() && o.getId().getValue().equals(value));
                if (!matchesActiveOption) {
                    throw new DomainException(ProductErrorCode.INVALID_ATTRIBUTE_VALUE);
                }
            }
        }
    }
}
