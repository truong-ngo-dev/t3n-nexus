package vn.t3nexus.catalog.application.brand;

import vn.t3nexus.catalog.domain.brand.Brand;
import vn.t3nexus.catalog.domain.brand.BrandStatus;

import java.util.UUID;

/** Thương hiệu trả về sau lệnh ghi (kèm phiên bản sau thay đổi) và cho đọc chi tiết. */
public record BrandView(UUID id, String name, BrandStatus status, long version) {

    public static BrandView of(Brand brand, long version) {
        return new BrandView(brand.getId().getValue(), brand.getName(), brand.getStatus(), version);
    }
}
