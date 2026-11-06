package vn.t3nexus.catalog.domain.product;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.category.CategoryAttributeAssignment;
import vn.t3nexus.catalog.domain.product.MissingPublishCondition.Code;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Điều kiện còn thiếu để đăng sản phẩm (INV-CAT-91, RM-CAT-04).
 * Trả đủ mọi điều kiện thiếu trong một lượt để người bán sửa một thể (không dừng sớm).
 * Bao gồm: thuộc tính bắt buộc chưa có, chưa có biến thể hoạt động, chưa có ảnh,
 * người bán chưa được duyệt, và danh mục không dùng được.
 * Thuộc tính bắt buộc chỉ ép khi mẫu còn dùng được, và dòng làm trục tính là đã điền.
 */
public final class MissingPublishConditions {

    private MissingPublishConditions() {
    }

    public static List<MissingPublishCondition> of(Product product, List<CategoryAttributeAssignment> assignments,
                                                   Map<AttributeTemplateId, AttributeTemplate> templates,
                                                   boolean hasActiveVariant) {
        return of(product, assignments, templates, hasActiveVariant, true, true);
    }

    public static List<MissingPublishCondition> of(Product product, List<CategoryAttributeAssignment> assignments,
                                                   Map<AttributeTemplateId, AttributeTemplate> templates,
                                                   boolean hasActiveVariant, boolean sellerApproved,
                                                   boolean categoryUsable) {
        Set<AttributeTemplateId> present = new HashSet<>();
        product.getAttributes().forEach(a -> present.add(a.templateId()));

        List<MissingPublishCondition> missing = new ArrayList<>();
        assignments.stream()
                .filter(CategoryAttributeAssignment::isRequired)
                .sorted(Comparator.comparingInt(CategoryAttributeAssignment::getDisplayOrder))
                .forEach(a -> {
                    AttributeTemplate template = templates.get(a.getAttributeTemplateId());
                    if (template != null && template.isUsable() && !present.contains(a.getAttributeTemplateId())) {
                        missing.add(new MissingPublishCondition(Code.REQUIRED_ATTRIBUTE_MISSING, a.getAttributeTemplateId()));
                    }
                });
        if (!hasActiveVariant) {
            missing.add(new MissingPublishCondition(Code.NO_ACTIVE_VARIANT, null));
        }
        if (product.getImages().isEmpty()) {
            missing.add(new MissingPublishCondition(Code.NO_IMAGE, null));
        }
        if (!sellerApproved) {
            missing.add(new MissingPublishCondition(Code.SELLER_NOT_APPROVED, null));
        }
        if (!categoryUsable) {
            missing.add(new MissingPublishCondition(Code.CATEGORY_NOT_USABLE, null));
        }
        return List.copyOf(missing);
    }
}
