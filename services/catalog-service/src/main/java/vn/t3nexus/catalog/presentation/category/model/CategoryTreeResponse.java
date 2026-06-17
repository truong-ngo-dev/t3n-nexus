package vn.t3nexus.catalog.presentation.category.model;

import java.util.List;

public record CategoryTreeResponse(
        String id,
        String name,
        String slug,
        int level,
        String imageUrl,
        List<CategoryTreeResponse> children
) {}
