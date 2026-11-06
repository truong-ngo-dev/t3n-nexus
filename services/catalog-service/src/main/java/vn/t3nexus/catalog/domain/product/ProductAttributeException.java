package vn.t3nexus.catalog.domain.product;

import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.exception.ErrorCode;

/** Lỗi 400 của một thuộc tính (giá trị hoặc trục); {@link #attribute()} là tên kỹ thuật của mẫu thuộc tính. */
public class ProductAttributeException extends DomainException {

    private final String attribute;

    public ProductAttributeException(ErrorCode errorCode, String attribute) {
        super(errorCode, "Thuộc tính không hợp lệ: " + attribute);
        this.attribute = attribute;
    }

    public String attribute() {
        return attribute;
    }
}
