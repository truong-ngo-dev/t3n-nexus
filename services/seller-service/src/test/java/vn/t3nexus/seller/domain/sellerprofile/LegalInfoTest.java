package vn.t3nexus.seller.domain.sellerprofile;

import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm thử giá trị LegalInfo (thông tin pháp lý).
 */
class LegalInfoTest {

    private static final String VALID_ADDRESS = "Số 1 Đại Cồ Việt, Hai Bà Trưng, Hà Nội";

    @Test
    void threeValidSellerTypesCreatedSuccessfully() {
        // INDIVIDUAL (không mã số thuế)
        LegalInfo individualNoTax = LegalInfo.of(
                SellerType.INDIVIDUAL, "Nguyễn Văn A", null, "079123456789", null, VALID_ADDRESS
        );
        assertThat(individualNoTax.type()).isEqualTo(SellerType.INDIVIDUAL);
        assertThat(individualNoTax.legalName()).isEqualTo("Nguyễn Văn A");
        assertThat(individualNoTax.representativeName()).isNull();
        assertThat(individualNoTax.identityNumber().value()).isEqualTo("079123456789");
        assertThat(individualNoTax.taxCode()).isNull();
        assertThat(individualNoTax.businessAddress().value()).isEqualTo(VALID_ADDRESS);
        assertThat(individualNoTax.identityKey()).isEqualTo("079123456789");

        // INDIVIDUAL khai thêm mã số thuế hợp lệ
        LegalInfo individualWithTax = LegalInfo.of(
                SellerType.INDIVIDUAL, "Nguyễn Văn A", null, "079123456789", "0123456789", VALID_ADDRESS
        );
        assertThat(individualWithTax.taxCode()).isNotNull();
        assertThat(individualWithTax.taxCode().value()).isEqualTo("0123456789");
        // identityKey() của INDIVIDUAL vẫn là số căn cước kể cả khi khai mã số thuế
        assertThat(individualWithTax.identityKey()).isEqualTo("079123456789");

        // HOUSEHOLD (có mã số thuế)
        LegalInfo household = LegalInfo.of(
                SellerType.HOUSEHOLD, "Hộ kinh doanh X", null, "079123456789", "0123456789-001", VALID_ADDRESS
        );
        assertThat(household.type()).isEqualTo(SellerType.HOUSEHOLD);
        assertThat(household.taxCode().value()).isEqualTo("0123456789001");
        // identityKey() của HOUSEHOLD là mã số thuế (13 chữ số liền với dạng có gạch)
        assertThat(household.identityKey()).isEqualTo("0123456789001");

        // BUSINESS (có người đại diện và mã số thuế)
        LegalInfo business = LegalInfo.of(
                SellerType.BUSINESS, "Công ty T3N", "Trần Văn B", "079123456789", "0123456789012", VALID_ADDRESS
        );
        assertThat(business.type()).isEqualTo(SellerType.BUSINESS);
        assertThat(business.representativeName()).isEqualTo("Trần Văn B");
        assertThat(business.taxCode().value()).isEqualTo("0123456789012");
        // identityKey() của BUSINESS là mã số thuế
        assertThat(business.identityKey()).isEqualTo("0123456789012");
    }

    @Test
    void businessWithoutRepresentativeThrowsException() {
        // BUSINESS thiếu người đại diện lỗi LEGAL_INFO_REQUIRED field representativeName
        assertThatThrownBy(() -> LegalInfo.of(
                SellerType.BUSINESS, "Công ty T3N", null, "079123456789", "0123456789", VALID_ADDRESS
        )).isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.LEGAL_INFO_REQUIRED);
                    assertThat(fe.field()).isEqualTo("representativeName");
                });

        assertThatThrownBy(() -> LegalInfo.of(
                SellerType.BUSINESS, "Công ty T3N", "   ", "079123456789", "0123456789", VALID_ADDRESS
        )).isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.LEGAL_INFO_REQUIRED);
                    assertThat(fe.field()).isEqualTo("representativeName");
                });
    }

    @Test
    void householdAndBusinessWithoutTaxCodeThrowsException() {
        // HOUSEHOLD thiếu mã số thuế lỗi LEGAL_INFO_REQUIRED field taxCode
        assertThatThrownBy(() -> LegalInfo.of(
                SellerType.HOUSEHOLD, "Hộ kinh doanh X", null, "079123456789", null, VALID_ADDRESS
        )).isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.LEGAL_INFO_REQUIRED);
                    assertThat(fe.field()).isEqualTo("taxCode");
                });

        // BUSINESS thiếu mã số thuế lỗi LEGAL_INFO_REQUIRED field taxCode
        assertThatThrownBy(() -> LegalInfo.of(
                SellerType.BUSINESS, "Công ty T3N", "Trần Văn B", "079123456789", "  ", VALID_ADDRESS
        )).isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.LEGAL_INFO_REQUIRED);
                    assertThat(fe.field()).isEqualTo("taxCode");
                });
    }

    @Test
    void missingRequiredFieldsThrowsLegalInfoRequired() {
        // thiếu legalName
        assertThatThrownBy(() -> LegalInfo.of(
                SellerType.INDIVIDUAL, null, null, "079123456789", null, VALID_ADDRESS
        )).isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.LEGAL_INFO_REQUIRED);
                    assertThat(fe.field()).isEqualTo("legalName");
                });

        // thiếu identityNumber
        assertThatThrownBy(() -> LegalInfo.of(
                SellerType.INDIVIDUAL, "Nguyễn Văn A", null, null, null, VALID_ADDRESS
        )).isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.LEGAL_INFO_REQUIRED);
                    assertThat(fe.field()).isEqualTo("identityNumber");
                });

        // thiếu businessAddress
        assertThatThrownBy(() -> LegalInfo.of(
                SellerType.INDIVIDUAL, "Nguyễn Văn A", null, "079123456789", null, null
        )).isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.LEGAL_INFO_REQUIRED);
                    assertThat(fe.field()).isEqualTo("businessAddress");
                });
    }

    @Test
    void individualIgnoresRepresentativeName() {
        // INDIVIDUAL truyền representativeName thì representativeName() là null
        LegalInfo info = LegalInfo.of(
                SellerType.INDIVIDUAL, "Nguyễn Văn A", "Người thừa", "079123456789", null, VALID_ADDRESS
        );
        assertThat(info.representativeName()).isNull();
    }

    @Test
    void individualWithInvalidTaxCodeThrowsInvalidTaxCode() {
        // INDIVIDUAL truyền mã số thuế sai định dạng thì lỗi INVALID_TAX_CODE
        String badTax = "12345678901x";
        assertThatThrownBy(() -> LegalInfo.of(
                SellerType.INDIVIDUAL, "Nguyễn Văn A", null, "079123456789", badTax, VALID_ADDRESS
        )).isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.INVALID_TAX_CODE);
                    assertThat(fe.field()).isEqualTo("taxCode");
                    assertThat(fe.getMessage()).doesNotContain(badTax);
                });
    }

    @Test
    void invalidIdentityNumberThrowsInvalidIdentityNumber() {
        // số căn cước sai thì INVALID_IDENTITY_NUMBER
        String badIdentity = "12345678901x";
        assertThatThrownBy(() -> LegalInfo.of(
                SellerType.INDIVIDUAL, "Nguyễn Văn A", null, badIdentity, null, VALID_ADDRESS
        )).isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.INVALID_IDENTITY_NUMBER);
                    assertThat(fe.field()).isEqualTo("identityNumber");
                    assertThat(fe.getMessage()).doesNotContain(badIdentity);
                });
    }

    @Test
    void legalNameLengthBoundaries() {
        // legalName 1 ký tự lỗi SELLER_FIELD_INVALID field legalName
        assertThatThrownBy(() -> LegalInfo.of(
                SellerType.INDIVIDUAL, "A", null, "079123456789", null, VALID_ADDRESS
        )).isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(fe.field()).isEqualTo("legalName");
                });

        // 151 ký tự lỗi SELLER_FIELD_INVALID field legalName
        String input151 = "12345678901x" + "a".repeat(139);
        assertThatThrownBy(() -> LegalInfo.of(
                SellerType.INDIVIDUAL, input151, null, "079123456789", null, VALID_ADDRESS
        )).isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(fe.field()).isEqualTo("legalName");
                    assertThat(fe.getMessage()).doesNotContain(input151);
                });
    }

    @Test
    void nullTypeThrowsFieldException() {
        // type null lỗi field sellerType
        assertThatThrownBy(() -> LegalInfo.of(
                null, "Nguyễn Văn A", null, "079123456789", null, VALID_ADDRESS
        )).isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(fe.field()).isEqualTo("sellerType");
                });
    }

    @Test
    void validationOrderPrioritizesLegalNameOverIdentityNumber() {
        // THỨ TỰ KIỂM: khi thiếu đồng thời legalName và identityNumber thì lỗi là của legalName
        assertThatThrownBy(() -> LegalInfo.of(
                SellerType.INDIVIDUAL, null, null, null, null, VALID_ADDRESS
        )).isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.LEGAL_INFO_REQUIRED);
                    assertThat(fe.field()).isEqualTo("legalName");
                });
    }

    @Test
    void validationOrderPrioritizesIdentityNumberOverTaxCode() {
        // THỨ TỰ KIỂM: khi identityNumber sai định dạng và taxCode thiếu (HOUSEHOLD) thì lỗi là INVALID_IDENTITY_NUMBER
        String badIdentity = "12345678901x";
        assertThatThrownBy(() -> LegalInfo.of(
                SellerType.HOUSEHOLD, "Hộ kinh doanh X", null, badIdentity, null, VALID_ADDRESS
        )).isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.INVALID_IDENTITY_NUMBER);
                    assertThat(fe.field()).isEqualTo("identityNumber");
                    assertThat(fe.getMessage()).doesNotContain(badIdentity);
                });
    }

    @Test
    void maskSensoryDataInToString() {
        // toString() bằng LegalInfo[type=INDIVIDUAL] và không chứa số căn cước, mã số thuế, địa chỉ, tên
        LegalInfo info = LegalInfo.of(
                SellerType.INDIVIDUAL, "Nguyễn Văn A", null, "079123456789", "0123456789", VALID_ADDRESS
        );
        assertThat(info.toString()).isEqualTo("LegalInfo[type=INDIVIDUAL]");
        assertThat(info.toString()).doesNotContain("079123456789");
        assertThat(info.toString()).doesNotContain("0123456789");
        assertThat(info.toString()).doesNotContain("Nguyễn Văn A");
        assertThat(info.toString()).doesNotContain("Đại Cồ Việt");
    }
}
