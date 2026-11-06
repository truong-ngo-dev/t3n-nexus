package vn.t3nexus.seller.domain.sellerprofile;

import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm thử giá trị Address.
 */
class AddressTest {

    @Test
    void boundaryLengthsValidatedAfterCollapsingWhitespace() {
        // 10 ký tự được
        Address addr10 = Address.of("  1234567890  ", "businessAddress");
        assertThat(addr10.value()).isEqualTo("1234567890");

        // 9 ký tự lỗi
        String input9 = "123456789";
        assertThatThrownBy(() -> Address.of("  " + input9 + "  ", "businessAddress"))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(fe.field()).isEqualTo("businessAddress");
                    assertThat(fe.getMessage()).doesNotContain(input9);
                });

        // 300 ký tự được
        String str300 = "a".repeat(300);
        Address addr300 = Address.of("  " + str300 + "  ", "contact.address");
        assertThat(addr300.value()).isEqualTo(str300);

        // 301 ký tự lỗi
        String input301 = "12345678901x" + "a".repeat(289);
        assertThatThrownBy(() -> Address.of("  " + input301 + "  ", "contact.address"))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(fe.field()).isEqualTo("contact.address");
                    assertThat(fe.getMessage()).doesNotContain(input301);
                });
    }

    @Test
    void nullRejectedWithPassedField() {
        // null lỗi với field businessAddress
        assertThatThrownBy(() -> Address.of(null, "businessAddress"))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(fe.field()).isEqualTo("businessAddress");
                });

        // null lỗi với field contact.address
        assertThatThrownBy(() -> Address.of(null, "contact.address"))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(fe.field()).isEqualTo("contact.address");
                });
    }

    @Test
    void maskSensoryDataInToString() {
        // toString() bằng Address[***]
        Address addr = Address.of("Số 1 Đại Cồ Việt, Hai Bà Trưng, Hà Nội", "businessAddress");
        assertThat(addr.toString()).isEqualTo("Address[***]");
        assertThat(addr.toString()).doesNotContain("Đại Cồ Việt");
    }

    @Test
    void equalsAndHashCodeBasedOnValue() {
        Address addr1 = Address.of("Số 1  Đại Cồ   Việt", "businessAddress");
        Address addr2 = Address.of("Số 1 Đại Cồ Việt", "contact.address");
        assertThat(addr1).isEqualTo(addr2);
        assertThat(addr1.hashCode()).isEqualTo(addr2.hashCode());
    }
}
