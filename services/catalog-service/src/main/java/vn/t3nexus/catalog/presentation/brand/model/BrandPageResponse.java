package vn.t3nexus.catalog.presentation.brand.model;

import java.util.List;
import java.util.UUID;

/** Danh sách cho mọi người đã đăng nhập: chỉ thương hiệu đang dùng, một trang cộng tổng số bản ghi khớp bộ lọc. */
public record BrandPageResponse(List<Item> items, long total) {

    public record Item(UUID id, String name) {}
}
