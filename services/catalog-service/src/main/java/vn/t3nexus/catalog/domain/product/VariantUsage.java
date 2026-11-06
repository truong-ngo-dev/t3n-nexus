package vn.t3nexus.catalog.domain.product;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.variant.Variant;
import vn.t3nexus.catalog.domain.variant.VariantAttributePair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Biến thể của một sản phẩm nhìn từ phía sản phẩm: có biến thể nào không, có biến thể hoạt động không, và những giá trị
 * trục nào đang được tổ hợp của biến thể (kể cả đã ngừng) dùng. Cơ sở của khóa trục (INV-CAT-33).
 */
public record VariantUsage(boolean hasVariants, boolean hasActiveVariant,
                           Map<AttributeTemplateId, Set<AttributeOptionId>> usedOptions) {

    public VariantUsage {
        usedOptions = Map.copyOf(usedOptions);
    }

    /** Tính từ mọi biến thể của sản phẩm, kể cả đã ngừng (một biến thể ngừng vẫn giữ giá trị trục của nó). */
    public static VariantUsage of(List<Variant> variants) {
        Map<AttributeTemplateId, Set<AttributeOptionId>> used = new HashMap<>();
        for (Variant variant : variants) {
            for (VariantAttributePair pair : variant.getCombination()) {
                used.computeIfAbsent(pair.templateId(), k -> new HashSet<>()).add(pair.optionId());
            }
        }
        return new VariantUsage(!variants.isEmpty(), variants.stream().anyMatch(Variant::isActive), used);
    }

    public static VariantUsage none() {
        return new VariantUsage(false, false, Map.of());
    }
}
