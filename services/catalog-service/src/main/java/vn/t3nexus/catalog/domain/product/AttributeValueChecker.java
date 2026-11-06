package vn.t3nexus.catalog.domain.product;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateErrorCode;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.catalog.domain.category.AttributeConstraintChecker;
import vn.t3nexus.catalog.domain.category.CategoryAttributeAssignment;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Kiểm giá trị thuộc tính của sản phẩm (INV-CAT-32, 33). Hàm thuần: use case đọc danh mục, mẫu và giá trị đã lưu rồi đưa
 * vào; không gọi kho. {@code today} là ngày để diễn giải mốc tương đối ({@code now-30d}) của ràng buộc ngày.
 * <p>
 * <b>Giá trị đã lưu và không đổi được miễn kiểm</b> (grandfathering): Quản trị tắt tùy chọn, tắt mẫu, siết ràng buộc hay
 * bỏ gán thuộc tính sau khi sản phẩm được tạo thì người bán vẫn sửa phần khác được. Chỉ phần <i>mới hoặc đổi</i> bị kiểm
 * theo trạng thái hiện tại; với kiểu chọn nhiều, kiểm từng tùy chọn mới. Thuộc tính bắt buộc không kiểm ở đây (nháp được
 * soạn từng bước): {@link MissingPublishConditions} báo thiếu.
 */
public final class AttributeValueChecker {

    private AttributeValueChecker() {
    }

    public static List<ProductAttribute> check(List<AttributeInput> submitted, List<ProductAttribute> previous,
                                               List<CategoryAttributeAssignment> assignments,
                                               Map<AttributeTemplateId, AttributeTemplate> templates,
                                               ProductLimits limits, LocalDate today) {
        Map<AttributeTemplateId, ProductAttribute> previousById = new HashMap<>();
        previous.forEach(a -> previousById.put(a.templateId(), a));
        Map<AttributeTemplateId, CategoryAttributeAssignment> assignmentById = new HashMap<>();
        assignments.forEach(a -> assignmentById.put(a.getAttributeTemplateId(), a));

        Set<AttributeTemplateId> seen = new HashSet<>();
        List<ProductAttribute> result = new ArrayList<>();
        int axes = 0;
        for (AttributeInput input : submitted) {
            AttributeTemplate template = templates.get(input.templateId());
            if (template == null) {
                throw new DomainException(AttributeTemplateErrorCode.ATTRIBUTE_TEMPLATE_NOT_FOUND);
            }
            String name = template.getName();
            if (!seen.add(input.templateId())) {
                throw invalid(name);
            }
            if (input.axis() && ++axes > limits.maxAxes()) {
                throw axis(name);
            }
            result.add(checkOne(template, input, previousById.get(input.templateId()),
                    assignmentById.get(input.templateId()), today));
        }
        return List.copyOf(result);
    }

    private static ProductAttribute checkOne(AttributeTemplate template, AttributeInput input, ProductAttribute previous,
                                             CategoryAttributeAssignment assignment, LocalDate today) {
        String name = template.getName();
        InputType type = template.getInputType();
        List<String> values = input.values() == null ? List.of() : input.values();
        if (input.axis() && (type != InputType.SINGLE_SELECT || values.isEmpty())) {
            throw axis(name);
        }
        return type.isSelect()
                ? checkOptions(template, input.axis(), values, previous, assignment)
                : checkScalar(template, values, previous, assignment, today);
    }

    private static ProductAttribute checkOptions(AttributeTemplate template, boolean axis, List<String> values,
                                                 ProductAttribute previous, CategoryAttributeAssignment assignment) {
        String name = template.getName();
        if (values.isEmpty()) {
            throw invalid(name);
        }
        Set<AttributeOptionId> ids = new LinkedHashSet<>();
        for (String raw : values) {
            if (!ids.add(optionId(raw, name))) {
                throw invalid(name);
            }
        }
        if (template.getInputType() == InputType.SINGLE_SELECT && !axis && ids.size() != 1) {
            throw invalid(name);
        }

        Set<AttributeOptionId> before = previous == null ? Set.of() : previous.optionIds();
        Set<AttributeOptionId> added = new LinkedHashSet<>(ids);
        added.removeAll(before);
        boolean changed = previous == null || previous.axis() != axis || !added.isEmpty();
        if (changed) {
            requireAssignedAndUsable(template, assignment);
            for (AttributeOptionId option : added) {
                boolean activeOption = template.getOptions().stream()
                        .anyMatch(o -> o.getId().equals(option) && o.isActive());
                if (!activeOption) {
                    throw invalid(name);
                }
            }
        }
        boolean setChanged = !ids.equals(before);
        if (setChanged && template.getInputType() == InputType.MULTI_SELECT && assignment != null
                && !AttributeConstraintChecker.satisfiesSelectionCount(assignment.getConstraints(), ids.size())) {
            throw invalid(name);
        }
        return ProductAttribute.ofOptions(template.getId(), axis, ids);
    }

    private static ProductAttribute checkScalar(AttributeTemplate template, List<String> values,
                                                ProductAttribute previous, CategoryAttributeAssignment assignment,
                                                LocalDate today) {
        String name = template.getName();
        if (values.size() != 1 || values.get(0) == null) {
            throw invalid(name);
        }
        String raw = values.get(0);
        String canonical = canonical(template.getInputType(), raw);
        if (previous != null && canonical.equals(previous.value())) {
            return previous;
        }
        requireAssignedAndUsable(template, assignment);
        if (!template.isValidRawValue(raw)
                || !AttributeConstraintChecker.satisfies(assignment.getConstraints(), template.getInputType(),
                        canonical, today)) {
            throw invalid(name);
        }
        return ProductAttribute.ofValue(template.getId(), canonical);
    }

    private static void requireAssignedAndUsable(AttributeTemplate template, CategoryAttributeAssignment assignment) {
        if (assignment == null || !template.isUsable()) {
            throw invalid(template.getName());
        }
    }

    private static AttributeOptionId optionId(String raw, String attribute) {
        try {
            return AttributeOptionId.of(UUID.fromString(raw.strip()));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw invalid(attribute);
        }
    }

    /** Dạng chuẩn để so và lưu: chữ cắt khoảng trắng, số bỏ số 0 thừa, đúng/sai chữ thường, ngày giữ nguyên. */
    private static String canonical(InputType type, String raw) {
        String trimmed = raw.strip();
        return switch (type) {
            case NUMBER -> {
                try {
                    yield new BigDecimal(trimmed).stripTrailingZeros().toPlainString();
                } catch (NumberFormatException e) {
                    yield trimmed;
                }
            }
            case BOOLEAN -> trimmed.toLowerCase(Locale.ROOT);
            default -> trimmed;
        };
    }

    private static ProductAttributeException invalid(String attribute) {
        return new ProductAttributeException(ProductErrorCode.PRODUCT_ATTRIBUTE_INVALID, attribute);
    }

    private static ProductAttributeException axis(String attribute) {
        return new ProductAttributeException(ProductErrorCode.PRODUCT_AXIS_INVALID, attribute);
    }
}
