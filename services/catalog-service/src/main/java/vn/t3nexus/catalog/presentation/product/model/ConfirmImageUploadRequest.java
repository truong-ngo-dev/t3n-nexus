package vn.t3nexus.catalog.presentation.product.model;

import jakarta.validation.constraints.NotBlank;

public record ConfirmImageUploadRequest(@NotBlank String objectKey) {}
