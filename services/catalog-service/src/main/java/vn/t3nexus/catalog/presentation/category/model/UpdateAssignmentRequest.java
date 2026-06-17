package vn.t3nexus.catalog.presentation.category.model;

public record UpdateAssignmentRequest(
        boolean variantDefining,
        boolean required,
        boolean filterable,
        int displayOrder
) {}
