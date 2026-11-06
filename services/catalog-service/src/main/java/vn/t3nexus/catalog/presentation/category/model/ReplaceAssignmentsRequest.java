package vn.t3nexus.catalog.presentation.category.model;

import jakarta.validation.constraints.NotNull;
import vn.t3nexus.catalog.domain.category.AttributeConstraints;

import java.util.List;
import java.util.UUID;

/** Danh sách mong muốn cuối cùng của các thuộc tính được gán; có thể rỗng (bỏ hết). */
public record ReplaceAssignmentsRequest(@NotNull List<Item> items) {

    /** {@code required} bỏ qua = false, {@code displayOrder} bỏ qua = 0 (api.yaml). Khai kiểu bọc để thiếu trường không thành lỗi 400. */
    public record Item(@NotNull UUID templateId, Boolean required, Integer displayOrder, AttributeConstraints constraints) {}
}
