package vn.t3nexus.catalog.presentation.category.model;

import vn.t3nexus.catalog.domain.attributetemplate.InputType;

import java.util.List;

/** Droplist khi Admin gán thêm attribute cho danh mục lá — chỉ template dùng được, chưa gán. */
public record AssignableAttributeTemplateResponse(List<Item> options) {

    public record Item(String id, String displayName, InputType inputType) {}
}
