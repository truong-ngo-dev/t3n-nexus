package vn.t3nexus.catalog.domain.variant;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateErrorCode;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateStatus;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.product.ProductAttributeValue;
import vn.t3nexus.catalog.domain.product.ProductErrorCode;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tổ hợp phân loại hợp lệ của 1 đơn vị bán được mới (analysis.md AGG-CAT-05, INV-CAT-97, INV-CAT-99).
 * <ul>
 *   <li><b>Đúng 1 giá trị cho mỗi trục</b> Sản phẩm đã đánh dấu {@code isVariantDefining} — không thiếu, không thừa,
 *       không có 2 giá trị cùng 1 trục, không có cặp cho thuộc tính không phải trục (đó là thông số chung).</li>
 *   <li>Giá trị nằm trong tập Sản phẩm đã khai cho trục (INV-CAT-99 — Liên tục).</li>
 *   <li>Thuộc tính và giá trị <b>đang dùng được</b> lúc tạo (INV-CAT-97 — Tiên quyết); Admin tắt sau đó không làm
 *       đơn vị cũ sai.</li>
 * </ul>
 */
public final class VariantCombinationPolicy {

    private VariantCombinationPolicy() {}

    public static void validate(Product product, List<VariantAttributePair> pairs,
                                Map<String, AttributeTemplate> templatesById) {
        Map<String, ProductAttributeValue> declaredByTemplate = product.getAttributeValues().stream()
                .collect(Collectors.toMap(av -> av.templateId().getValue(), av -> av));
        Set<String> axisIds = declaredByTemplate.values().stream()
                .filter(ProductAttributeValue::isVariantDefining)
                .map(av -> av.templateId().getValue())
                .collect(Collectors.toSet());

        Set<String> seenAxes = new HashSet<>();
        for (VariantAttributePair pair : pairs) {
            String templateId = pair.templateId().getValue();

            if (!seenAxes.add(templateId)) {
                throw new DomainException(ProductErrorCode.VARIANT_DUPLICATE_AXIS);
            }
            if (!axisIds.contains(templateId)) {
                throw new DomainException(ProductErrorCode.VARIANT_ATTRIBUTE_NOT_AXIS);
            }

            AttributeTemplate template = templatesById.get(templateId);
            if (template == null) {
                throw new DomainException(AttributeTemplateErrorCode.TEMPLATE_NOT_FOUND);
            }
            if (template.getStatus() == AttributeTemplateStatus.INACTIVE) {
                throw new DomainException(AttributeTemplateErrorCode.TEMPLATE_INACTIVE);
            }

            var option = template.getOptions().stream()
                    .filter(o -> o.getId().equals(pair.optionId()))
                    .findFirst()
                    .orElseThrow(() -> new DomainException(AttributeTemplateErrorCode.OPTION_NOT_FOUND));
            if (!option.isActive()) {
                throw new DomainException(ProductErrorCode.INVALID_ATTRIBUTE_VALUE);
            }

            if (!declaredByTemplate.get(templateId).values().contains(pair.optionId().getValue())) {
                throw new DomainException(ProductErrorCode.VARIANT_OPTION_NOT_DECLARED_BY_PRODUCT);
            }
        }

        if (!seenAxes.containsAll(axisIds)) {
            throw new DomainException(ProductErrorCode.VARIANT_MISSING_VARIANT_DEFINING_ATTRIBUTE);
        }
    }
}
