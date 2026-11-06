package vn.t3nexus.catalog.presentation.attributetemplate.model;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateStatus;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;

import java.util.List;
import java.util.UUID;

public record AttributeTemplateSummaryResponse(List<Item> items, long total) {

    public record Item(UUID id, String name, String displayName, InputType inputType, String unit,
                       AttributeTemplateStatus status, boolean usable, int optionCount) {}
}
