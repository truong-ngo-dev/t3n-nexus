package vn.t3nexus.seller.domain.sellerprofile;

import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm thử giá trị TaxCode (mã số thuế).
 */
class TaxCodeTest {

    @Test
    void tenDigitsAccepted() {
        // "0123456789" (10) được
        TaxCode tax = TaxCode.of("0123456789");
        assertThat(tax.value()).isEqualTo("0123456789");
    }

    @Test
    void thirteenDigitsAccepted() {
        // "0123456789012" (13) được
        TaxCode tax = TaxCode.of("0123456789012");
        assertThat(tax.value()).isEqualTo("0123456789012");
    }

    @Test
    void tenDigitsWithDashAndThreeDigitsAcceptedAndHyphenRemoved() {
        // "0123456789-001" được và value() bằng "0123456789001"
        TaxCode tax = TaxCode.of("0123456789-001");
        assertThat(tax.value()).isEqualTo("0123456789001");
    }

    @Test
    void invalidDigitCountsRejected() {
        // 9 chữ số lỗi
        String input9 = "012345678";
        assertThatThrownBy(() -> TaxCode.of(input9))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.INVALID_TAX_CODE);
                    assertThat(fe.field()).isEqualTo("taxCode");
                    assertThat(fe.getMessage()).doesNotContain(input9);
                });

        // 11 chữ số lỗi
        String input11 = "01234567891";
        assertThatThrownBy(() -> TaxCode.of(input11))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.INVALID_TAX_CODE);
                    assertThat(fe.field()).isEqualTo("taxCode");
                    assertThat(fe.getMessage()).doesNotContain(input11);
                });

        // 12 chữ số lỗi
        String input12 = "012345678901";
        assertThatThrownBy(() -> TaxCode.of(input12))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.INVALID_TAX_CODE);
                    assertThat(fe.field()).isEqualTo("taxCode");
                    assertThat(fe.getMessage()).doesNotContain(input12);
                });
    }

    @Test
    void invalidDashFormatRejected() {
        // "0123456789-01" lỗi
        String inputDash2 = "0123456789-01";
        assertThatThrownBy(() -> TaxCode.of(inputDash2))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.INVALID_TAX_CODE);
                    assertThat(fe.field()).isEqualTo("taxCode");
                    assertThat(fe.getMessage()).doesNotContain(inputDash2);
                });
    }

    @Test
    void lettersRejected() {
        // có chữ cái lỗi
        String inputLetters = "012345678901x";
        assertThatThrownBy(() -> TaxCode.of(inputLetters))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.INVALID_TAX_CODE);
                    assertThat(fe.field()).isEqualTo("taxCode");
                    assertThat(fe.getMessage()).doesNotContain(inputLetters);
                });
    }

    @Test
    void nullRejected() {
        // null lỗi
        assertThatThrownBy(() -> TaxCode.of(null))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.INVALID_TAX_CODE);
                    assertThat(fe.field()).isEqualTo("taxCode");
                });
    }

    @Test
    void maskSensoryDataInToString() {
        // toString() bằng TaxCode[***]
        TaxCode tax = TaxCode.of("0123456789");
        assertThat(tax.toString()).isEqualTo("TaxCode[***]");
        assertThat(tax.toString()).doesNotContain("0123456789");
    }

    @Test
    void equalsAndHashCodeBasedOnValue() {
        TaxCode tax1 = TaxCode.of("0123456789-001");
        TaxCode tax2 = TaxCode.of("0123456789001");
        assertThat(tax1).isEqualTo(tax2);
        assertThat(tax1.hashCode()).isEqualTo(tax2.hashCode());
    }
}
