package vn.t3nexus.seller.domain.sellerprofile;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

import java.util.Objects;
import java.util.UUID;

/**
 * Mã định danh hồ sơ người bán.
 */
public class SellerProfileId extends AbstractId<UUID> implements Id<UUID> {

    private SellerProfileId(UUID value) {
        super(Objects.requireNonNull(value, "id"));
    }

    public static SellerProfileId of(UUID value) {
        return new SellerProfileId(value);
    }
}
