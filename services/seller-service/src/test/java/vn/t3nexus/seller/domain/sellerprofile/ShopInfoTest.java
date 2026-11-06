package vn.t3nexus.seller.domain.sellerprofile;

import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm thử giá trị ShopInfo (thông tin gian hàng).
 */
class ShopInfoTest {

    private static final Contact VALID_CONTACT = Contact.of("Nguyễn Văn A", "0912345678", null);

    @Test
    void validShopInfoCreatedSuccessfully() {
        // hợp lệ
        ShopInfo shopInfo = ShopInfo.of("Cửa Hàng Xanh", "Mô tả shop", VALID_CONTACT);
        assertThat(shopInfo.shopName().display()).isEqualTo("Cửa Hàng Xanh");
        assertThat(shopInfo.description()).isEqualTo("Mô tả shop");
        assertThat(shopInfo.contact()).isEqualTo(VALID_CONTACT);
    }

    @Test
    void nullOrBlankDescriptionYieldsNull() {
        // mô tả null/trắng thì description() null
        ShopInfo s1 = ShopInfo.of("Cửa Hàng Xanh", null, VALID_CONTACT);
        assertThat(s1.description()).isNull();

        ShopInfo s2 = ShopInfo.of("Cửa Hàng Xanh", "    ", VALID_CONTACT);
        assertThat(s2.description()).isNull();
    }

    @Test
    void descriptionLengthBoundary() {
        // mô tả đúng 500 ký tự được
        String desc500 = "a".repeat(500);
        ShopInfo s500 = ShopInfo.of("Cửa Hàng Xanh", desc500, VALID_CONTACT);
        assertThat(s500.description()).isEqualTo(desc500);

        // 501 lỗi (description)
        String desc501 = "12345678901x" + "a".repeat(489);
        assertThatThrownBy(() -> ShopInfo.of("Cửa Hàng Xanh", desc501, VALID_CONTACT))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(fe.field()).isEqualTo("description");
                    assertThat(fe.getMessage()).doesNotContain(desc501);
                });
    }

    @Test
    void nullContactThrowsFieldException() {
        // contact null lỗi (contact)
        assertThatThrownBy(() -> ShopInfo.of("Cửa Hàng Xanh", "Mô tả", null))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(fe.field()).isEqualTo("contact");
                });
    }

    @Test
    void invalidShopNameThrowsFieldException() {
        // tên sai lỗi (shopName)
        String badName = "12345678901x";
        assertThatThrownBy(() -> ShopInfo.of("a", "Mô tả", VALID_CONTACT))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(fe.field()).isEqualTo("shopName");
                });
    }

    @Test
    void equalsWithSameDisplayNameAndFields() {
        ShopInfo s1 = ShopInfo.of("Cửa Hàng A", "Mô tả", VALID_CONTACT);
        ShopInfo s2 = ShopInfo.of("  Cửa   Hàng A ", "Mô tả", VALID_CONTACT);
        assertThat(s1).isEqualTo(s2);
        assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
    }

    @Test
    void toStringContainsDisplayNameAndExcludesDescriptionAndPhone() {
        // toString chứa display của tên và KHÔNG chứa mô tả hay số điện thoại
        ShopInfo shopInfo = ShopInfo.of("Cửa Hàng Xanh", "Mô tả bí mật 12345678901x", VALID_CONTACT);
        assertThat(shopInfo.toString()).isEqualTo("ShopInfo[shopName=Cửa Hàng Xanh]");
        assertThat(shopInfo.toString()).doesNotContain("Mô tả bí mật");
        assertThat(shopInfo.toString()).doesNotContain("0912345678");
    }

    @Test
    void shopInfosDifferingOnlyInCaseOrDiacriticsAreNotEqual() {
        // đổi cách viết (hoa thường, dấu) là thay đổi của người bán, dù khóa so sánh tên giống nhau
        ShopInfo a = ShopInfo.of("Cửa Hàng Hoa", null, VALID_CONTACT);
        ShopInfo b = ShopInfo.of("CUA HANG HOA", null, VALID_CONTACT);
        assertThat(a.shopName().key()).isEqualTo(b.shopName().key());
        assertThat(a).isNotEqualTo(b);
        assertThat(a).isEqualTo(ShopInfo.of("Cửa Hàng Hoa", null, VALID_CONTACT));
    }
}
