package vn.t3nexus.catalog.presentation.brand.model;

import java.util.List;

/** Droplist cho seller khi khai brand cho sản phẩm. */
public record BrandOptionResponse(List<Item> options) {

    public record Item(String id, String name) {}
}
