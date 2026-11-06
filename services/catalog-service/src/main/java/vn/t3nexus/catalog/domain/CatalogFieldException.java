package vn.t3nexus.catalog.domain;

import vn.t3nexus.lib.common.domain.exception.DomainException;

/** Dữ liệu vào sai ở một trường (400 {@code CATALOG_FIELD_INVALID}); {@link #field()} nêu tên trường. */
public class CatalogFieldException extends DomainException {

    private final String field;

    public CatalogFieldException(String field) {
        super(CatalogErrorCode.CATALOG_FIELD_INVALID, "Trường không hợp lệ: " + field);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
