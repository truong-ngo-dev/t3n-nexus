package vn.t3nexus.seller.domain.sellerprofile;

import java.util.Objects;

/**
 * Thông tin gian hàng của người bán.
 */
public final class ShopInfo {

    private final ShopName shopName;
    private final String description;
    private final Contact contact;

    private ShopInfo(ShopName shopName, String description, Contact contact) {
        this.shopName = shopName;
        this.description = description;
        this.contact = contact;
    }

    public static ShopInfo of(String shopName, String description, Contact contact) {
        ShopName shopNameVo = ShopName.of(shopName);

        String desc = null;
        if (description != null && !description.isBlank()) {
            desc = description.strip();
            if (desc.length() > 500) {
                throw new SellerFieldException(SellerErrorCode.SELLER_FIELD_INVALID, "description");
            }
        }

        if (contact == null) {
            throw new SellerFieldException(SellerErrorCode.SELLER_FIELD_INVALID, "contact");
        }

        return new ShopInfo(shopNameVo, desc, contact);
    }

    public ShopName shopName() {
        return shopName;
    }

    public String description() {
        return description;
    }

    public Contact contact() {
        return contact;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShopInfo shopInfo = (ShopInfo) o;
        // So theo bản hiển thị, không theo khóa: đổi chỉ hoa thường hay dấu vẫn là một thay đổi của người bán
        return Objects.equals(shopName.display(), shopInfo.shopName.display()) &&
                Objects.equals(description, shopInfo.description) &&
                Objects.equals(contact, shopInfo.contact);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shopName.display(), description, contact);
    }

    @Override
    public String toString() {
        return "ShopInfo[shopName=" + shopName.display() + "]";
    }
}
