package vn.t3nexus.catalog.domain.variant;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.product.ProductAttribute;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tổ hợp hợp lệ của một biến thể mới (INV-CAT-33, 94): đúng một giá trị cho mỗi trục của sản phẩm (thuộc tính đánh dấu
 * {@code axis}), không thừa không thiếu, mỗi giá trị nằm trong tập giá trị của trục, và mẫu cùng tùy chọn còn dùng được
 * lúc tạo (Quản trị tắt sau đó không làm biến thể cũ sai). Sản phẩm không có trục chỉ nhận tổ hợp rỗng (INV-CAT-44).
 * Hàm thuần; sai là {@link VariantErrorCode#VARIANT_COMBINATION_INVALID}.
 */
public final class VariantCombinationPolicy {

    private VariantCombinationPolicy() {
    }

    public static void validate(Product product, List<VariantAttributePair> pairs,
                                Map<AttributeTemplateId, AttributeTemplate> templates) {
        Map<AttributeTemplateId, Set<AttributeOptionId>> axes = new HashMap<>();
        for (ProductAttribute attribute : product.getAttributes()) {
            if (attribute.axis()) {
                axes.put(attribute.templateId(), attribute.optionIds());
            }
        }

        Set<AttributeTemplateId> chosen = new HashSet<>();
        for (VariantAttributePair pair : pairs) {
            Set<AttributeOptionId> allowed = axes.get(pair.templateId());
            if (allowed == null || !chosen.add(pair.templateId()) || !allowed.contains(pair.optionId())) {
                throw invalid();
            }
            AttributeTemplate template = templates.get(pair.templateId());
            if (template == null || !template.isUsable() || template.getOptions().stream()
                    .noneMatch(o -> o.getId().equals(pair.optionId()) && o.isActive())) {
                throw invalid();
            }
        }
        if (!chosen.equals(axes.keySet())) {
            throw invalid();
        }
    }

    private static DomainException invalid() {
        return new DomainException(VariantErrorCode.VARIANT_COMBINATION_INVALID);
    }
}
