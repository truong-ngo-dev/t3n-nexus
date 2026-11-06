package vn.t3nexus.seller.domain.sellerprofile;

import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.common.domain.exception.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm thử enum mã lỗi SellerErrorCode theo đúng hợp đồng.
 */
class SellerErrorCodeTest {

    @Test
    void allEnumConstantsAdhereToContract() {
        for (SellerErrorCode code : SellerErrorCode.values()) {
            // code() trả về đúng tên hằng
            assertThat(code.code()).isEqualTo(code.name());

            // messageKey() đúng định dạng error.seller.<tên viết thường>
            String expectedKey = "error.seller." + code.name().toLowerCase(java.util.Locale.ROOT);
            assertThat(code.messageKey()).isEqualTo(expectedKey);

            // defaultMessage() không được rỗng
            assertThat(code.defaultMessage()).isNotBlank();
        }
    }

    @Test
    void httpStatusCodesMatchContractTable() {
        // 400 Bad Request
        assertThat(SellerErrorCode.LEGAL_INFO_REQUIRED.httpStatus()).isEqualTo(400);
        assertThat(SellerErrorCode.INVALID_IDENTITY_NUMBER.httpStatus()).isEqualTo(400);
        assertThat(SellerErrorCode.INVALID_TAX_CODE.httpStatus()).isEqualTo(400);
        assertThat(SellerErrorCode.SELLER_FIELD_INVALID.httpStatus()).isEqualTo(400);
        assertThat(SellerErrorCode.REJECTION_REASON_REQUIRED.httpStatus()).isEqualTo(400);

        // 403 Forbidden
        assertThat(SellerErrorCode.SELLER_ROLE_NOT_ALLOWED.httpStatus()).isEqualTo(403);

        // 404 Not Found
        assertThat(SellerErrorCode.SELLER_PROFILE_NOT_FOUND.httpStatus()).isEqualTo(404);

        // 409 Conflict
        assertThat(SellerErrorCode.SELLER_PROFILE_EXISTS.httpStatus()).isEqualTo(409);
        assertThat(SellerErrorCode.SHOP_NAME_TAKEN.httpStatus()).isEqualTo(409);
        assertThat(SellerErrorCode.IDENTITY_ALREADY_REGISTERED.httpStatus()).isEqualTo(409);
        assertThat(SellerErrorCode.LEGAL_INFO_LOCKED.httpStatus()).isEqualTo(409);
        assertThat(SellerErrorCode.INVALID_STATE.httpStatus()).isEqualTo(409);
        assertThat(SellerErrorCode.CONCURRENT_UPDATE.httpStatus()).isEqualTo(409);
        assertThat(SellerErrorCode.STALE_VERSION.httpStatus()).isEqualTo(409);
    }

    @Test
    void implementsErrorCodeInterface() {
        assertThat(ErrorCode.class.isAssignableFrom(SellerErrorCode.class)).isTrue();
    }
}
