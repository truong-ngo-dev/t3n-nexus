package vn.t3nexus.seller.domain.sellerprofile;

import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm thử giá trị Phone (số điện thoại người liên hệ).
 */
class PhoneTest {

    @Test
    void standardTenDigitsAccepted() {
        // "0912345678" được
        Phone phone = Phone.of("0912345678");
        assertThat(phone.value()).isEqualTo("0912345678");
    }

    @Test
    void variousFormatsNormalizedToStandardTenDigits() {
        // "+84912345678", "84912345678", "091 234 5678", "091.234.5678", "091-234-5678" đều cho value() bằng "0912345678"
        assertThat(Phone.of("+84912345678").value()).isEqualTo("0912345678");
        assertThat(Phone.of("84912345678").value()).isEqualTo("0912345678");
        assertThat(Phone.of("091 234 5678").value()).isEqualTo("0912345678");
        assertThat(Phone.of("091.234.5678").value()).isEqualTo("0912345678");
        assertThat(Phone.of("091-234-5678").value()).isEqualTo("0912345678");
    }

    @Test
    void validAndInvalidPrefixes() {
        // đầu số 03, 05, 07, 08, 09 được
        assertThat(Phone.of("0312345678").value()).isEqualTo("0312345678");
        assertThat(Phone.of("0512345678").value()).isEqualTo("0512345678");
        assertThat(Phone.of("0712345678").value()).isEqualTo("0712345678");
        assertThat(Phone.of("0812345678").value()).isEqualTo("0812345678");
        assertThat(Phone.of("0912345678").value()).isEqualTo("0912345678");

        // 02 và 01 lỗi
        String input02 = "0212345678";
        assertThatThrownBy(() -> Phone.of(input02))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(fe.field()).isEqualTo("contact.phone");
                    assertThat(fe.getMessage()).doesNotContain(input02);
                });

        String input01 = "0112345678";
        assertThatThrownBy(() -> Phone.of(input01))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(fe.field()).isEqualTo("contact.phone");
                    assertThat(fe.getMessage()).doesNotContain(input01);
                });
    }

    @Test
    void invalidDigitCountsRejected() {
        // 9 hoặc 11 chữ số lỗi
        String input9 = "091234567";
        assertThatThrownBy(() -> Phone.of(input9))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(fe.field()).isEqualTo("contact.phone");
                    assertThat(fe.getMessage()).doesNotContain(input9);
                });

        String input11 = "09123456789";
        assertThatThrownBy(() -> Phone.of(input11))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(fe.field()).isEqualTo("contact.phone");
                    assertThat(fe.getMessage()).doesNotContain(input11);
                });
    }

    @Test
    void nullRejected() {
        // null lỗi
        assertThatThrownBy(() -> Phone.of(null))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(fe.field()).isEqualTo("contact.phone");
                });
    }

    @Test
    void maskSensoryDataInToString() {
        // toString() bằng Phone[***]
        Phone phone = Phone.of("0912345678");
        assertThat(phone.toString()).isEqualTo("Phone[***]");
        assertThat(phone.toString()).doesNotContain("0912345678");
    }

    @Test
    void equalsAndHashCodeBasedOnValue() {
        Phone phone1 = Phone.of("+84912345678");
        Phone phone2 = Phone.of("0912345678");
        assertThat(phone1).isEqualTo(phone2);
        assertThat(phone1.hashCode()).isEqualTo(phone2.hashCode());
    }
}
