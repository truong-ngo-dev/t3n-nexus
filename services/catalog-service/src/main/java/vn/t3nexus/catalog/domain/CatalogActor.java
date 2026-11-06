package vn.t3nexus.catalog.domain;

import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Người thực hiện thao tác, dựng từ token ở lớp trình bày. Lệnh ghi cấu trúc danh mục hàng chỉ dành cho {@code ADMIN};
 * lệnh sản phẩm và biến thể dành cho người bán, vai {@code USER} (INV-CAT-30).
 */
public record CatalogActor(UUID accountId, String role) {

    public CatalogActor {
        Objects.requireNonNull(accountId, "accountId");
        role = role == null ? "" : role.strip().toUpperCase(Locale.ROOT);
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isSeller() {
        return "USER".equals(role);
    }

    public void requireAdmin() {
        if (!isAdmin()) {
            throw new DomainException(CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED);
        }
    }

    public void requireSeller() {
        if (!isSeller()) {
            throw new DomainException(CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED);
        }
    }
}
