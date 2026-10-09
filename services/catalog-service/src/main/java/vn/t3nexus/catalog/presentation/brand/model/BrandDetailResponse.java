package vn.t3nexus.catalog.presentation.brand.model;

import vn.t3nexus.catalog.domain.brand.BrandStatus;

import java.time.Instant;

/** Chi tiết quản trị. */
public record BrandDetailResponse(
        String id,
        String name,
        String slug,
        BrandStatus status,
        Instant createdAt,
        Instant updatedAt
) {}
