package vn.t3nexus.catalog.presentation.brand.model;

import vn.t3nexus.catalog.application.brand.BrandView;
import vn.t3nexus.catalog.domain.brand.BrandStatus;

import java.util.UUID;

/** Thương hiệu cho quản trị: kèm trạng thái và phiên bản sau thay đổi. */
public record BrandResponse(UUID id, String name, BrandStatus status, long version) {

    public static BrandResponse from(BrandView view) {
        return new BrandResponse(view.id(), view.name(), view.status(), view.version());
    }
}
