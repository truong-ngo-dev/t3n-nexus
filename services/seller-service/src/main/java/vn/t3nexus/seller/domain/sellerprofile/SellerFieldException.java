package vn.t3nexus.seller.domain.sellerprofile;

import vn.t3nexus.lib.common.domain.exception.DomainException;

/**
 * Ngoại lệ miền liên quan đến trường dữ liệu của người bán không hợp lệ.
 */
public class SellerFieldException extends DomainException {

    private final String field;

    public SellerFieldException(SellerErrorCode errorCode, String field) {
        super(errorCode, "Trường không hợp lệ: " + field);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
