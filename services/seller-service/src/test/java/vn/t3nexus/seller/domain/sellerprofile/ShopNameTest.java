package vn.t3nexus.seller.domain.sellerprofile;

import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm thử giá trị ShopName.
 */
class ShopNameTest {

    @Test
    void normalizesDisplayAndKeyCorrectly() {
        // "  Cửa   Hàng  A " cho display "Cửa Hàng A", key "cua hang a"
        ShopName shopName = ShopName.of("  Cửa   Hàng  A ");
        assertThat(shopName.display()).isEqualTo("Cửa Hàng A");
        assertThat(shopName.key()).isEqualTo("cua hang a");
        assertThat(shopName.toString()).isEqualTo("Cửa Hàng A");
    }

    @Test
    void removesVietnameseDiacriticsAndNormalizesSpecialLetters() {
        // "Đồ Chơi Trẻ Em" cho key "do choi tre em"
        ShopName shopName = ShopName.of("Đồ Chơi Trẻ Em");
        assertThat(shopName.key()).isEqualTo("do choi tre em");
    }

    @Test
    void equalsAndHashCodeBasedOnKey() {
        // "CỬA HÀNG A" và "cua hang a" bằng nhau (equals, hashCode)
        ShopName shop1 = ShopName.of("CỬA HÀNG A");
        ShopName shop2 = ShopName.of("cua hang a");

        assertThat(shop1).isEqualTo(shop2);
        assertThat(shop1.hashCode()).isEqualTo(shop2.hashCode());
    }

    @Test
    void boundaryLengthsValidatedAfterCollapsingWhitespace() {
        // 2 ký tự lỗi
        String input2 = "12345678901x_ab"; // nhận diện đầu vào không bị rò rỉ
        assertThatThrownBy(() -> ShopName.of("  ab  "))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(fe.field()).isEqualTo("shopName");
                    assertThat(fe.getMessage()).doesNotContain("ab");
                });

        // 3 ký tự được
        ShopName shop3 = ShopName.of("  abc  ");
        assertThat(shop3.display()).isEqualTo("abc");

        // 60 ký tự được
        String sixty = "a".repeat(60);
        ShopName shop60 = ShopName.of("  " + sixty + "  ");
        assertThat(shop60.display()).isEqualTo(sixty);

        // 61 ký tự lỗi
        String input61 = "12345678901x" + "a".repeat(49);
        assertThatThrownBy(() -> ShopName.of("  " + input61 + "  "))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(fe.field()).isEqualTo("shopName");
                    assertThat(fe.getMessage()).doesNotContain(input61);
                });
    }

    @Test
    void nullAndBlankInputsAreRejected() {
        // null lỗi
        assertThatThrownBy(() -> ShopName.of(null))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(fe.field()).isEqualTo("shopName");
                });

        // chuỗi toàn khoảng trắng lỗi
        String blankInput = "12345678901x    ";
        assertThatThrownBy(() -> ShopName.of("    "))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(fe.field()).isEqualTo("shopName");
                });
    }
}
