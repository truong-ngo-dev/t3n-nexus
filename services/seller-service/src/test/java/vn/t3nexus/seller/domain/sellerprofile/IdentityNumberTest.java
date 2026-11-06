package vn.t3nexus.seller.domain.sellerprofile;

import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm thử giá trị IdentityNumber (số căn cước).
 */
class IdentityNumberTest {

    @Test
    void validTwelveDigitsAccepted() {
        // 12 chữ số hợp lệ
        IdentityNumber id = IdentityNumber.of("079123456789");
        assertThat(id.value()).isEqualTo("079123456789");
    }

    @Test
    void spacesBetweenDigitsAreRemoved() {
        // có khoảng trắng chen giữa ("079 123 456 789") được và value() không có khoảng trắng
        IdentityNumber id = IdentityNumber.of("079 123 456 789");
        assertThat(id.value()).isEqualTo("079123456789");
    }

    @Test
    void invalidDigitCountsRejected() {
        // 11 chữ số lỗi
        String input11 = "12345678901";
        assertThatThrownBy(() -> IdentityNumber.of(input11))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.INVALID_IDENTITY_NUMBER);
                    assertThat(fe.field()).isEqualTo("identityNumber");
                    assertThat(fe.getMessage()).doesNotContain(input11);
                });

        // 13 chữ số lỗi
        String input13 = "1234567890123";
        assertThatThrownBy(() -> IdentityNumber.of(input13))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.INVALID_IDENTITY_NUMBER);
                    assertThat(fe.field()).isEqualTo("identityNumber");
                    assertThat(fe.getMessage()).doesNotContain(input13);
                });
    }

    @Test
    void lettersRejected() {
        // có chữ cái lỗi
        String inputWithLetter = "12345678901x";
        assertThatThrownBy(() -> IdentityNumber.of(inputWithLetter))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.INVALID_IDENTITY_NUMBER);
                    assertThat(fe.field()).isEqualTo("identityNumber");
                    assertThat(fe.getMessage()).doesNotContain(inputWithLetter);
                });
    }

    @Test
    void nullRejected() {
        // null lỗi
        assertThatThrownBy(() -> IdentityNumber.of(null))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.INVALID_IDENTITY_NUMBER);
                    assertThat(fe.field()).isEqualTo("identityNumber");
                });
    }

    @Test
    void maskSensoryDataInToString() {
        // toString() bằng đúng IdentityNumber[***] và không chứa chữ số
        IdentityNumber id = IdentityNumber.of("079123456789");
        assertThat(id.toString()).isEqualTo("IdentityNumber[***]");
        assertThat(id.toString()).doesNotContain("079123456789");
    }

    @Test
    void equalsAndHashCodeBasedOnValue() {
        IdentityNumber id1 = IdentityNumber.of("079 123 456 789");
        IdentityNumber id2 = IdentityNumber.of("079123456789");
        assertThat(id1).isEqualTo(id2);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }
}
