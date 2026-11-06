package vn.t3nexus.seller.domain.sellerprofile;

import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Tác nhân thực hiện thao tác trên hồ sơ người bán.
 */
public record SellerActor(UUID accountId, String role) {

    public SellerActor {
        Objects.requireNonNull(accountId, "accountId");
        role = role == null ? "" : role.strip().toUpperCase(Locale.ROOT);
    }

    public boolean isUser() {
        return "USER".equals(role);
    }

    public void requireUser() {
        if (!isUser()) {
            throw new DomainException(SellerErrorCode.SELLER_ROLE_NOT_ALLOWED);
        }
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public void requireAdmin() {
        if (!isAdmin()) {
            throw new DomainException(SellerErrorCode.SELLER_ROLE_NOT_ALLOWED);
        }
    }
}
