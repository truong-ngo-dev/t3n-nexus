package vn.t3nexus.catalog.presentation.category;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.t3nexus.catalog.application.category.*;
import vn.t3nexus.catalog.presentation.category.model.*;
import vn.t3nexus.lib.web.commons.response.ApiResponse;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CategoryController {

    private final GetCategoryTree getCategoryTree;
    private final GetCategoryAttributes getCategoryAttributes;
    private final CreateCategory createCategory;
    private final UpdateCategory updateCategory;
    private final DeleteCategory deleteCategory;
    private final AssignAttributeToCategory assignAttributeToCategory;
    private final UpdateCategoryAttributeAssignment updateCategoryAttributeAssignment;
    private final RemoveCategoryAttributeAssignment removeCategoryAttributeAssignment;

    @GetMapping("/api/categories")
    public ApiResponse<List<CategoryTreeResponse>> getCategoryTree() {
        List<CategoryTreeResponse> roots = getCategoryTree.handle(new GetCategoryTree.Query())
                .roots().stream()
                .map(this::toTreeResponse)
                .toList();
        return ApiResponse.ok(roots);
    }

    @GetMapping("/api/categories/{id}/attributes")
    public ApiResponse<List<CategoryAttributeResponse>> getCategoryAttributes(@PathVariable String id) {
        List<CategoryAttributeResponse> attributes =
                getCategoryAttributes.handle(new GetCategoryAttributes.Query(id))
                        .attributes().stream()
                        .map(dto -> new CategoryAttributeResponse(
                                dto.templateId(), dto.name(), dto.displayName(),
                                dto.inputType(), dto.scope(),
                                dto.variantDefining(), dto.required(),
                                dto.filterable(), dto.displayOrder()))
                        .toList();
        return ApiResponse.ok(attributes);
    }

    @PostMapping("/api/admin/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<String> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        CreateCategory.Result result = createCategory.handle(
                new CreateCategory.Command(request.name(), request.slug(), request.parentId()));
        return ApiResponse.ok(result.id());
    }

    @PutMapping("/api/admin/categories/{id}")
    public ApiResponse<Void> updateCategory(@PathVariable String id,
                                            @Valid @RequestBody UpdateCategoryRequest request) {
        updateCategory.handle(new UpdateCategory.Command(id, request.name(), request.imageUrl()));
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/api/admin/categories/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable String id) {
        deleteCategory.handle(new DeleteCategory.Command(id));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/admin/categories/{id}/attributes")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> assignAttribute(@PathVariable String id,
                                             @Valid @RequestBody AssignAttributeRequest request) {
        assignAttributeToCategory.handle(new AssignAttributeToCategory.Command(
                id, request.templateId(),
                request.variantDefining(), request.required(),
                request.filterable(), request.displayOrder()));
        return ApiResponse.ok(null);
    }

    @PutMapping("/api/admin/categories/{id}/attributes/{templateId}")
    public ApiResponse<Void> updateAssignment(@PathVariable String id,
                                              @PathVariable String templateId,
                                              @Valid @RequestBody UpdateAssignmentRequest request) {
        updateCategoryAttributeAssignment.handle(new UpdateCategoryAttributeAssignment.Command(
                id, templateId,
                request.variantDefining(), request.required(),
                request.filterable(), request.displayOrder()));
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/api/admin/categories/{id}/attributes/{templateId}")
    public ApiResponse<Void> removeAssignment(@PathVariable String id,
                                              @PathVariable String templateId) {
        removeCategoryAttributeAssignment.handle(
                new RemoveCategoryAttributeAssignment.Command(id, templateId));
        return ApiResponse.ok(null);
    }

    private CategoryTreeResponse toTreeResponse(GetCategoryTree.CategoryTreeNode node) {
        List<CategoryTreeResponse> children = node.children().stream()
                .map(this::toTreeResponse)
                .toList();
        return new CategoryTreeResponse(
                node.id(), node.name(), node.slug(),
                node.level(), node.imageUrl(), children);
    }
}
