package vn.t3nexus.catalog.presentation.attributetemplate.model;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Mọi optionId của template, theo thứ tự mong muốn — mỗi option đúng 1 lần. */
public record ReorderAttributeOptionsRequest(@NotEmpty List<String> optionIds) {}
