package vn.t3nexus.catalog.presentation.attributetemplate.model;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateStatus;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;

import java.util.List;

/** Danh sách quản trị: 1 trang tóm tắt + tổng số bản ghi khớp bộ lọc. */
public record AttributeTemplateSummaryResponse(List<Item> items, long total) {

    public record Item(
            String id,
            String name,
            String displayName,
            InputType inputType,
            String unit,
            AttributeTemplateStatus status,
            int optionCount
    ) {}
}
