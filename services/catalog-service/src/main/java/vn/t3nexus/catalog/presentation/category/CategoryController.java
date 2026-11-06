package vn.t3nexus.catalog.presentation.category;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.t3nexus.catalog.application.category.ActivateCategory;
import vn.t3nexus.catalog.application.category.CategoryView;
import vn.t3nexus.catalog.application.category.CreateCategory;
import vn.t3nexus.catalog.application.category.DeactivateCategory;
import vn.t3nexus.catalog.application.category.DeleteCategory;
import vn.t3nexus.catalog.application.category.GetAdminCategoryTree;
import vn.t3nexus.catalog.application.category.GetCategoryAssignments;
import vn.t3nexus.catalog.application.category.GetCategoryAttributes;
import vn.t3nexus.catalog.application.category.GetCategoryTree;
import vn.t3nexus.catalog.application.category.RenameCategory;
import vn.t3nexus.catalog.application.category.ReorderCategories;
import vn.t3nexus.catalog.application.category.ReplaceCategoryAttributes;
import vn.t3nexus.catalog.presentation.CatalogActorResolver;
import vn.t3nexus.catalog.presentation.category.model.AdminAssignmentResponse;
import vn.t3nexus.catalog.presentation.category.model.AdminCategoryNodeResponse;
import vn.t3nexus.catalog.presentation.category.model.CategoryAttributeResponse;
import vn.t3nexus.catalog.presentation.category.model.CategoryResponse;
import vn.t3nexus.catalog.presentation.category.model.CategoryTreeResponse;
import vn.t3nexus.catalog.presentation.category.model.CreateCategoryRequest;
import vn.t3nexus.catalog.presentation.category.model.RenameCategoryRequest;
import vn.t3nexus.catalog.presentation.category.model.ReorderCategoriesRequest;
import vn.t3nexus.catalog.presentation.category.model.ReplaceAssignmentsRequest;
import vn.t3nexus.lib.web.commons.response.ApiResponse;

import java.util.List;
import java.util.UUID;

/**
 * Quản danh mục và gán thuộc tính (CAP-CAT-01). Hai đường đọc {@code /api/categories/**} cho mọi người đã đăng nhập;
 * các đường {@code /api/admin/categories/**} cần vai {@code ADMIN}, kiểm trong use case.
 */
@RestController
@RequiredArgsConstructor
public class CategoryController {

    private final GetCategoryTree getCategoryTree;
    private final GetAdminCategoryTree getAdminCategoryTree;
    private final GetCategoryAttributes getCategoryAttributes;
    private final GetCategoryAssignments getCategoryAssignments;
    private final CreateCategory createCategory;
    private final RenameCategory renameCategory;
    private final ReorderCategories reorderCategories;
    private final DeactivateCategory deactivateCategory;
    private final ActivateCategory activateCategory;
    private final DeleteCategory deleteCategory;
    private final ReplaceCategoryAttributes replaceCategoryAttributes;

    @GetMapping("/api/categories")
    public ApiResponse<List<CategoryTreeResponse>> tree() {
        return ApiResponse.ok(getCategoryTree.handle(new GetCategoryTree.Query()).stream()
                .map(CategoryTreeResponse::from)
                .toList());
    }

    @GetMapping("/api/categories/{id}/attributes")
    public ApiResponse<List<CategoryAttributeResponse>> attributes(@PathVariable UUID id) {
        return ApiResponse.ok(getCategoryAttributes.handle(new GetCategoryAttributes.Query(id)).stream()
                .map(CategoryAttributeResponse::from)
                .toList());
    }

    @GetMapping("/api/admin/categories/tree")
    public ApiResponse<List<AdminCategoryNodeResponse>> adminTree(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(getAdminCategoryTree.handle(new GetAdminCategoryTree.Query(CatalogActorResolver.from(jwt)))
                .stream()
                .map(AdminCategoryNodeResponse::from)
                .toList());
    }

    @GetMapping("/api/admin/categories/{id}/assignments")
    public ApiResponse<List<AdminAssignmentResponse>> assignments(@AuthenticationPrincipal Jwt jwt,
                                                                  @PathVariable UUID id) {
        return ApiResponse.ok(getCategoryAssignments
                .handle(new GetCategoryAssignments.Query(CatalogActorResolver.from(jwt), id)).stream()
                .map(AdminAssignmentResponse::from)
                .toList());
    }

    @PostMapping("/api/admin/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CategoryResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                @RequestBody CreateCategoryRequest request) {
        return ok(createCategory.handle(
                new CreateCategory.Command(CatalogActorResolver.from(jwt), request.name(), request.parentId())));
    }

    @PutMapping("/api/admin/categories/reorder")
    public ApiResponse<List<CategoryResponse>> reorder(@AuthenticationPrincipal Jwt jwt,
                                                       @Valid @RequestBody ReorderCategoriesRequest request) {
        return ApiResponse.ok(reorderCategories.handle(new ReorderCategories.Command(
                        CatalogActorResolver.from(jwt), request.parentId(), request.categoryIds())).stream()
                .map(CategoryResponse::from)
                .toList());
    }

    @PutMapping("/api/admin/categories/{id}")
    public ApiResponse<CategoryResponse> rename(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                @RequestBody RenameCategoryRequest request) {
        return ok(renameCategory.handle(new RenameCategory.Command(CatalogActorResolver.from(jwt), id, request.name())));
    }

    @DeleteMapping("/api/admin/categories/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        deleteCategory.handle(new DeleteCategory.Command(CatalogActorResolver.from(jwt), id));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/admin/categories/{id}/deactivate")
    public ApiResponse<CategoryResponse> deactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ok(deactivateCategory.handle(new DeactivateCategory.Command(CatalogActorResolver.from(jwt), id)));
    }

    @PostMapping("/api/admin/categories/{id}/activate")
    public ApiResponse<CategoryResponse> activate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ok(activateCategory.handle(new ActivateCategory.Command(CatalogActorResolver.from(jwt), id)));
    }

    @PutMapping("/api/admin/categories/{id}/attributes")
    public ApiResponse<List<AdminAssignmentResponse>> replaceAttributes(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody ReplaceAssignmentsRequest request) {
        List<ReplaceCategoryAttributes.Item> items = request.items().stream()
                .map(i -> new ReplaceCategoryAttributes.Item(i.templateId(), Boolean.TRUE.equals(i.required()),
                        i.displayOrder() == null ? 0 : i.displayOrder(), i.constraints()))
                .toList();
        return ApiResponse.ok(replaceCategoryAttributes
                .handle(new ReplaceCategoryAttributes.Command(CatalogActorResolver.from(jwt), id, items)).stream()
                .map(AdminAssignmentResponse::from)
                .toList());
    }

    private static ApiResponse<CategoryResponse> ok(CategoryView view) {
        return ApiResponse.ok(CategoryResponse.from(view));
    }
}
