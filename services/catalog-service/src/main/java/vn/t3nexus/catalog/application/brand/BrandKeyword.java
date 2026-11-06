package vn.t3nexus.catalog.application.brand;

import vn.t3nexus.catalog.domain.NameKey;

/** Chuẩn hóa từ khóa tìm thương hiệu như tên (bỏ dấu, chữ thường, khoảng trắng); rỗng thì không lọc. */
final class BrandKeyword {

    private BrandKeyword() {
    }

    static String of(String keyword) {
        return keyword == null || keyword.isBlank() ? null : NameKey.of(keyword).key();
    }
}
