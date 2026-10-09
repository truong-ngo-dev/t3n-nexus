package vn.t3nexus.catalog.presentation.brand.model;

import java.util.List;

/** Danh sách công khai: 1 trang tóm tắt (chỉ ACTIVE) + tổng số bản ghi khớp bộ lọc. */
public record BrandPageResponse(List<BrandResponse> items, long total) {}
