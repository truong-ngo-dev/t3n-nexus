package vn.t3nexus.catalog.domain.product;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.lib.common.domain.model.ValueObject;

import java.util.List;

/**
 * {@code isVariantDefining} do Seller tự khai per-listing (không phải category áp đặt) — attribute nào
 * đánh dấu true bắt buộc phải xuất hiện trong MỌI {@code Variant.combination} của Product này (xem
 * {@code AddVariant}), chỉ hợp lệ khi template là SELECT. Cardinality của {@code values} không bị ràng
 * buộc bởi cờ này — attribute không variant-defining vẫn có thể khai nhiều giá trị (thuần thông tin,
 * không tách SKU).
 */
public record ProductAttributeValue(
        AttributeTemplateId templateId,
        List<String> values,
        boolean isVariantDefining
) implements ValueObject {

    public ProductAttributeValue {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("ProductAttributeValue.values must not be empty");
        }
        values = List.copyOf(values);
    }
}
