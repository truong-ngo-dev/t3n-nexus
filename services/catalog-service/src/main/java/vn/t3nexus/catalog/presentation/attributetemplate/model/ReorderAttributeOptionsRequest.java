package vn.t3nexus.catalog.presentation.attributetemplate.model;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ReorderAttributeOptionsRequest(@NotNull List<UUID> optionIds) {}
