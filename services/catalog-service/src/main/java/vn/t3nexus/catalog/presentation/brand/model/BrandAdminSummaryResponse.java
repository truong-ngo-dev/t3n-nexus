package vn.t3nexus.catalog.presentation.brand.model;

import vn.t3nexus.catalog.domain.brand.BrandStatus;

import java.util.List;

/** Danh sách quản trị: mọi trạng thái, 1 trang tóm tắt + tổng số bản ghi khớp bộ lọc. */
public record BrandAdminSummaryResponse(List<Item> items, long total) {

    public record Item(String id, String name, String slug, BrandStatus status) {}
}
