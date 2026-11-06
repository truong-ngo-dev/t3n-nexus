package vn.t3nexus.catalog.presentation.brand;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.t3nexus.catalog.application.brand.ActivateBrand;
import vn.t3nexus.catalog.application.brand.BrandView;
import vn.t3nexus.catalog.application.brand.CreateBrand;
import vn.t3nexus.catalog.application.brand.DeactivateBrand;
import vn.t3nexus.catalog.application.brand.GetBrand;
import vn.t3nexus.catalog.application.brand.ListAdminBrands;
import vn.t3nexus.catalog.application.brand.ListBrands;
import vn.t3nexus.catalog.application.brand.RenameBrand;
import vn.t3nexus.catalog.domain.brand.BrandStatus;
import vn.t3nexus.catalog.presentation.CatalogActorResolver;
import vn.t3nexus.catalog.presentation.brand.model.AdminBrandPageResponse;
import vn.t3nexus.catalog.presentation.brand.model.BrandNameRequest;
import vn.t3nexus.catalog.presentation.brand.model.BrandPageResponse;
import vn.t3nexus.catalog.presentation.brand.model.BrandResponse;
import vn.t3nexus.lib.web.commons.response.ApiResponse;

import java.util.List;
import java.util.UUID;

/**
 * Quản thương hiệu (CAP-CAT-03). Danh sách {@code /api/brands} cho mọi người đã đăng nhập; các đường
 * {@code /api/admin/brands} cần vai {@code ADMIN}, kiểm trong use case.
 */
@RestController
@RequiredArgsConstructor
public class BrandController {

    /** Trần kích thước trang; vượt thì cắt về trần. */
    private static final int MAX_PAGE_SIZE = 50;

    private final CreateBrand createBrand;
    private final RenameBrand renameBrand;
    private final DeactivateBrand deactivateBrand;
    private final ActivateBrand activateBrand;
    private final ListBrands listBrands;
    private final ListAdminBrands listAdminBrands;
    private final GetBrand getBrand;

    @GetMapping("/api/brands")
    public ApiResponse<BrandPageResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ListBrands.Result result = listBrands.handle(new ListBrands.Query(
                keyword, Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)));
        List<BrandPageResponse.Item> items = result.items().stream()
                .map(b -> new BrandPageResponse.Item(b.id(), b.name()))
                .toList();
        return ApiResponse.ok(new BrandPageResponse(items, result.total()));
    }

    @GetMapping("/api/admin/brands")
    public ApiResponse<AdminBrandPageResponse> listAdmin(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BrandStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ListAdminBrands.Result result = listAdminBrands.handle(new ListAdminBrands.Query(
                CatalogActorResolver.from(jwt), keyword, status,
                Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)));
        List<BrandResponse> items = result.items().stream().map(BrandResponse::from).toList();
        return ApiResponse.ok(new AdminBrandPageResponse(items, result.total()));
    }

    @GetMapping("/api/admin/brands/{id}")
    public ApiResponse<BrandResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ok(getBrand.handle(new GetBrand.Query(CatalogActorResolver.from(jwt), id)));
    }

    @PostMapping("/api/admin/brands")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BrandResponse> create(@AuthenticationPrincipal Jwt jwt, @RequestBody BrandNameRequest request) {
        return ok(createBrand.handle(new CreateBrand.Command(CatalogActorResolver.from(jwt), request.name())));
    }

    @PutMapping("/api/admin/brands/{id}")
    public ApiResponse<BrandResponse> rename(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                             @RequestBody BrandNameRequest request) {
        return ok(renameBrand.handle(new RenameBrand.Command(CatalogActorResolver.from(jwt), id, request.name())));
    }

    @PostMapping("/api/admin/brands/{id}/deactivate")
    public ApiResponse<BrandResponse> deactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ok(deactivateBrand.handle(new DeactivateBrand.Command(CatalogActorResolver.from(jwt), id)));
    }

    @PostMapping("/api/admin/brands/{id}/activate")
    public ApiResponse<BrandResponse> activate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ok(activateBrand.handle(new ActivateBrand.Command(CatalogActorResolver.from(jwt), id)));
    }

    private static ApiResponse<BrandResponse> ok(BrandView view) {
        return ApiResponse.ok(BrandResponse.from(view));
    }
}
