package vn.t3nexus.catalog.domain.product;

import vn.t3nexus.lib.common.domain.model.ValueObject;

public record WarrantyInfo(
        int months,
        String type,
        String coverage
) implements ValueObject {}
