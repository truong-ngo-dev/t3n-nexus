package vn.t3nexus.catalog.presentation.brand.model;

import java.util.List;

/** Danh sách quản trị: mọi trạng thái, một trang cộng tổng số bản ghi khớp bộ lọc. */
public record AdminBrandPageResponse(List<BrandResponse> items, long total) {}
