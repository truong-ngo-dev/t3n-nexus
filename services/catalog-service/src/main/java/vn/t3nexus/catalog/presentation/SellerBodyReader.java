package vn.t3nexus.catalog.presentation;

import tools.jackson.databind.JsonNode;
import vn.t3nexus.catalog.application.product.Patch;
import vn.t3nexus.catalog.domain.CatalogFieldException;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.product.AttributeInput;
import vn.t3nexus.catalog.domain.variant.VariantAttributePair;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Đọc nội dung yêu cầu của người bán từ {@link JsonNode} để phân biệt "vắng", "có giá trị", "null" (PATCH) và để bắt
 * sai kiểu theo từng trường (giá phải là số nguyên, mã phải là UUID). Sai là 400 {@code CATALOG_FIELD_INVALID} nêu tên trường.
 * Trường không biết (trạng thái, {@code version}, {@code sellerId}...) bị bỏ qua: chủ luôn lấy từ token.
 */
public final class SellerBodyReader {

    private SellerBodyReader() {
    }

    public static JsonNode requireObject(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new CatalogFieldException("body");
        }
        return body;
    }

    public static Patch<String> text(JsonNode parent, String name) {
        if (!parent.has(name)) {
            return Patch.absent();
        }
        JsonNode node = parent.get(name);
        if (node == null || node.isNull()) {
            return Patch.of(null);
        }
        if (node.isString()) {
            return Patch.of(node.asString());
        }
        throw new CatalogFieldException(name);
    }

    public static String textOrNull(JsonNode parent, String name) {
        return text(parent, name).value();
    }

    public static UUID requiredUuid(JsonNode parent, String name) {
        UUID id = uuid(parent, name).value();
        if (id == null) {
            throw new CatalogFieldException(name);
        }
        return id;
    }

    public static Patch<UUID> uuid(JsonNode parent, String name) {
        if (!parent.has(name)) {
            return Patch.absent();
        }
        JsonNode node = parent.get(name);
        if (node == null || node.isNull()) {
            return Patch.of(null);
        }
        if (node.isString()) {
            try {
                return Patch.of(UUID.fromString(node.asString()));
            } catch (IllegalArgumentException ignored) {
                // rơi xuống ném lỗi trường
            }
        }
        throw new CatalogFieldException(name);
    }

    /** Giá: chỉ nhận số nguyên; số có phần thập phân, chữ hay vượt kiểu long là 400 trường {@code name}. */
    public static Patch<Long> integer(JsonNode parent, String name) {
        if (!parent.has(name)) {
            return Patch.absent();
        }
        JsonNode node = parent.get(name);
        if (node == null || node.isNull()) {
            return Patch.of(null);
        }
        if (node.isIntegralNumber() && node.canConvertToLong()) {
            return Patch.of(node.asLong());
        }
        throw new CatalogFieldException(name);
    }

    public static long requiredInteger(JsonNode parent, String name) {
        Long value = integer(parent, name).value();
        if (value == null) {
            throw new CatalogFieldException(name);
        }
        return value;
    }

    /** {@code attributes}: mảng các {@code {templateId, values[], axis}}; vắng thì {@link Patch#absent()}. */
    public static Patch<List<AttributeInput>> attributes(JsonNode parent, String name) {
        if (!parent.has(name)) {
            return Patch.absent();
        }
        JsonNode node = parent.get(name);
        if (node == null || node.isNull()) {
            return Patch.of(List.of());
        }
        if (!node.isArray()) {
            throw new CatalogFieldException(name);
        }
        List<AttributeInput> result = new ArrayList<>();
        for (int i = 0; i < node.size(); i++) {
            JsonNode item = node.get(i);
            if (item == null || !item.isObject()) {
                throw new CatalogFieldException(name);
            }
            result.add(new AttributeInput(AttributeTemplateId.of(requiredUuid(item, "templateId")),
                    strings(item, "values"), flag(item, "axis")));
        }
        return Patch.of(List.copyOf(result));
    }

    /** {@code combination}: mảng các {@code {templateId, optionId}}; vắng là tổ hợp rỗng (biến thể mặc định). */
    public static List<VariantAttributePair> combination(JsonNode parent, String name) {
        if (!parent.has(name) || parent.get(name).isNull()) {
            return List.of();
        }
        JsonNode node = parent.get(name);
        if (!node.isArray()) {
            throw new CatalogFieldException(name);
        }
        List<VariantAttributePair> result = new ArrayList<>();
        for (int i = 0; i < node.size(); i++) {
            JsonNode item = node.get(i);
            if (item == null || !item.isObject()) {
                throw new CatalogFieldException(name);
            }
            result.add(new VariantAttributePair(AttributeTemplateId.of(requiredUuid(item, "templateId")),
                    AttributeOptionId.of(requiredUuid(item, "optionId"))));
        }
        return List.copyOf(result);
    }

    private static List<String> strings(JsonNode parent, String name) {
        if (!parent.has(name) || parent.get(name).isNull()) {
            return List.of();
        }
        JsonNode node = parent.get(name);
        if (!node.isArray()) {
            throw new CatalogFieldException(name);
        }
        List<String> values = new ArrayList<>();
        for (int i = 0; i < node.size(); i++) {
            JsonNode item = node.get(i);
            if (item == null || !item.isString()) {
                throw new CatalogFieldException(name);
            }
            values.add(item.asString());
        }
        return List.copyOf(values);
    }

    private static boolean flag(JsonNode parent, String name) {
        if (!parent.has(name) || parent.get(name).isNull()) {
            return false;
        }
        JsonNode node = parent.get(name);
        if (!node.isBoolean()) {
            throw new CatalogFieldException(name);
        }
        return node.asBoolean();
    }
}
