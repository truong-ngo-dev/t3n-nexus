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
    private final GetAdminCategoryTree getAdminCategoryTree;
    private final GetCategoryAttributes getCategoryAttributes;
    private final GetCategoryAttributeAssignments getCategoryAttributeAssignments;
    private final ListAssignableAttributeTemplates listAssignableAttributeTemplates;
    private final CreateCategory createCategory;
    private final UpdateCategory updateCategory;
    private final DeleteCategory deleteCategory;
    private final DeactivateCategory deactivateCategory;
    private final ActivateCategory activateCategory;
    private final ReorderCategories reorderCategories;
    private final ReplaceCategoryAttributeAssignments replaceCategoryAttributeAssignments;

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
                                dto.templateId(), dto.name(), dto.displayName(), dto.hint(),
                                dto.inputType(), dto.unit(), dto.required(), dto.constraints(), dto.displayOrder(),
                                dto.options().stream()
                                        .map(o -> new CategoryAttributeResponse.Option(o.id(), o.value(), o.displayValue()))
                                        .toList()))
                        .toList();
        return ApiResponse.ok(attributes);
    }

    @GetMapping("/api/admin/categories/tree")
    public ApiResponse<List<CategoryAdminTreeResponse>> getAdminCategoryTree() {
        List<CategoryAdminTreeResponse> roots = getAdminCategoryTree.handle(new GetAdminCategoryTree.Query())
                .roots().stream()
                .map(this::toAdminTreeResponse)
                .toList();
        return ApiResponse.ok(roots);
    }

    @GetMapping("/api/admin/categories/{id}/assignments")
    public ApiResponse<List<CategoryAssignmentDetailResponse>> getCategoryAttributeAssignments(@PathVariable String id) {
        List<CategoryAssignmentDetailResponse> items =
                getCategoryAttributeAssignments.handle(new GetCategoryAttributeAssignments.Query(id))
                        .items().stream()
                        .map(d -> new CategoryAssignmentDetailResponse(
                                d.templateId(), d.name(), d.displayName(), d.inputType(), d.templateStatus(),
                                d.required(), d.displayOrder(), d.constraints(), d.discovery()))
                        .toList();
        return ApiResponse.ok(items);
    }

    @GetMapping("/api/admin/categories/{id}/assignable-templates")
    public ApiResponse<AssignableAttributeTemplateResponse> listAssignableAttributeTemplates(
            @PathVariable String id, @RequestParam(required = false) String keyword) {
        List<AssignableAttributeTemplateResponse.Item> options =
                listAssignableAttributeTemplates.handle(new ListAssignableAttributeTemplates.Query(id, keyword))
                        .options().stream()
                        .map(o -> new AssignableAttributeTemplateResponse.Item(o.id(), o.displayName(), o.inputType()))
                        .toList();
        return ApiResponse.ok(new AssignableAttributeTemplateResponse(options));
    }

    @PostMapping("/api/admin/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<String> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        CreateCategory.Result result = createCategory.handle(
                new CreateCategory.Command(request.name(), request.parentId()));
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

    @PostMapping("/api/admin/categories/{id}/deactivate")
    public ApiResponse<Void> deactivateCategory(@PathVariable String id) {
        deactivateCategory.handle(new DeactivateCategory.Command(id));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/admin/categories/{id}/activate")
    public ApiResponse<Void> activateCategory(@PathVariable String id) {
        activateCategory.handle(new ActivateCategory.Command(id));
        return ApiResponse.ok(null);
    }

    @PutMapping("/api/admin/categories/reorder")
    public ApiResponse<Void> reorderCategories(@Valid @RequestBody ReorderCategoriesRequest request) {
        reorderCategories.handle(new ReorderCategories.Command(request.parentId(), request.categoryIds()));
        return ApiResponse.ok(null);
    }

    @PutMapping("/api/admin/categories/{id}/attributes")
    public ApiResponse<Void> replaceAttributes(
            @PathVariable String id,
            @Valid @RequestBody List<ReplaceCategoryAttributesRequest> request) {
        List<ReplaceCategoryAttributeAssignments.AttributeAssignmentItem> items = request.stream()
                .map(r -> new ReplaceCategoryAttributeAssignments.AttributeAssignmentItem(
                        r.templateId(), r.required(), r.displayOrder(), r.constraints(), r.discovery()))
                .toList();
        replaceCategoryAttributeAssignments.handle(
                new ReplaceCategoryAttributeAssignments.Command(id, items));
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

    private CategoryAdminTreeResponse toAdminTreeResponse(GetAdminCategoryTree.CategoryTreeNode node) {
        List<CategoryAdminTreeResponse> children = node.children().stream()
                .map(this::toAdminTreeResponse)
                .toList();
        return new CategoryAdminTreeResponse(
                node.id(), node.name(), node.slug(),
                node.level(), node.imageUrl(), node.status(), children);
    }
}
