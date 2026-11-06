package vn.t3nexus.seller.presentation.sellerprofile;

import tools.jackson.databind.JsonNode;
import vn.t3nexus.seller.application.sellerprofile.Patch;
import vn.t3nexus.seller.domain.sellerprofile.SellerErrorCode;
import vn.t3nexus.seller.domain.sellerprofile.SellerFieldException;
import vn.t3nexus.seller.domain.sellerprofile.SellerType;

/**
 * Tiện ích hỗ trợ đọc nội dung cập nhật từng phần từ JsonNode.
 */
public final class PatchBodyReader {

    private PatchBodyReader() {
    }

    public static JsonNode requireObject(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new SellerFieldException(SellerErrorCode.SELLER_FIELD_INVALID, "body");
        }
        return body;
    }

    public static JsonNode objectOrNull(JsonNode parent, String name) {
        if (parent == null || !parent.has(name)) {
            return null;
        }
        JsonNode node = parent.get(name);
        if (node == null || !node.isObject()) {
            throw new SellerFieldException(SellerErrorCode.SELLER_FIELD_INVALID, name);
        }
        return node;
    }

    public static Patch<String> text(JsonNode parent, String name) {
        if (parent == null || !parent.has(name)) {
            return Patch.absent();
        }
        JsonNode node = parent.get(name);
        if (node == null || node.isNull()) {
            return Patch.of(null);
        }
        if (node.isString()) {
            return Patch.of(node.asString());
        }
        throw new SellerFieldException(SellerErrorCode.SELLER_FIELD_INVALID, name);
    }

    public static Patch<SellerType> sellerType(JsonNode parent, String name) {
        if (parent == null || !parent.has(name)) {
            return Patch.absent();
        }
        JsonNode node = parent.get(name);
        if (node != null && node.isString()) {
            try {
                return Patch.of(SellerType.valueOf(node.asString()));
            } catch (IllegalArgumentException ignored) {
                // Rơi vào ném ngoại lệ bên dưới
            }
        }
        throw new SellerFieldException(SellerErrorCode.SELLER_FIELD_INVALID, name);
    }

    public static SellerType parseSellerType(String value) {
        if (value == null) {
            throw new SellerFieldException(SellerErrorCode.SELLER_FIELD_INVALID, "sellerType");
        }
        try {
            return SellerType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new SellerFieldException(SellerErrorCode.SELLER_FIELD_INVALID, "sellerType");
        }
    }
}
