package vn.t3nexus.catalog.presentation.brand;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.t3nexus.catalog.application.brand.*;
import vn.t3nexus.catalog.domain.brand.BrandStatus;
import vn.t3nexus.catalog.presentation.brand.model.*;
import vn.t3nexus.lib.web.commons.response.ApiResponse;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BrandController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CreateBrand createBrand;
    private final UpdateBrand updateBrand;
    private final DeactivateBrand deactivateBrand;
    private final ActivateBrand activateBrand;
    private final ListBrands listBrands;
    private final ListAdminBrands listAdminBrands;
    private final GetBrand getBrand;
    private final SearchBrandsForSeller searchBrandsForSeller;

    // ───────────── Public ─────────────

    @GetMapping("/api/brands")
    public ApiResponse<BrandPageResponse> listBrands(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ListBrands.Result result = listBrands.handle(new ListBrands.Query(
                blankToNull(keyword), Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)));
        List<BrandResponse> items = result.items().stream()
                .map(b -> new BrandResponse(b.id(), b.name(), b.slug()))
                .toList();
        return ApiResponse.ok(new BrandPageResponse(items, result.total()));
    }

    // ───────────── Seller ─────────────

    @GetMapping("/api/seller/brands")
    public ApiResponse<BrandOptionResponse> searchBrandsForSeller(@RequestParam(required = false) String keyword) {
        SearchBrandsForSeller.Result result = searchBrandsForSeller.handle(
                new SearchBrandsForSeller.Query(blankToNull(keyword)));
        List<BrandOptionResponse.Item> options = result.options().stream()
                .map(o -> new BrandOptionResponse.Item(o.id(), o.name()))
                .toList();
        return ApiResponse.ok(new BrandOptionResponse(options));
    }

    // ───────────── Admin ─────────────

    @GetMapping("/api/admin/brands")
    public ApiResponse<BrandAdminSummaryResponse> listAdminBrands(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BrandStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ListAdminBrands.Result result = listAdminBrands.handle(new ListAdminBrands.Query(
                blankToNull(keyword), status, Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)));
        List<BrandAdminSummaryResponse.Item> items = result.items().stream()
                .map(b -> new BrandAdminSummaryResponse.Item(b.id(), b.name(), b.slug(), b.status()))
                .toList();
        return ApiResponse.ok(new BrandAdminSummaryResponse(items, result.total()));
    }

    @GetMapping("/api/admin/brands/{id}")
    public ApiResponse<BrandDetailResponse> getBrand(@PathVariable String id) {
        GetBrand.Result b = getBrand.handle(new GetBrand.Query(id));
        return ApiResponse.ok(new BrandDetailResponse(
                b.id(), b.name(), b.slug(), b.status(), b.createdAt(), b.updatedAt()));
    }

    @PostMapping("/api/admin/brands")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BrandResponse> createBrand(@Valid @RequestBody CreateBrandRequest request) {
        CreateBrand.Result result = createBrand.handle(new CreateBrand.Command(request.name(), request.slug()));
        return ApiResponse.ok(new BrandResponse(result.id(), request.name(), request.slug()));
    }

    @PutMapping("/api/admin/brands/{id}")
    public ApiResponse<BrandResponse> updateBrand(@PathVariable String id,
                                                  @Valid @RequestBody UpdateBrandRequest request) {
        updateBrand.handle(new UpdateBrand.Command(id, request.name()));
        return ApiResponse.ok(new BrandResponse(id, request.name(), null));
    }

    @DeleteMapping("/api/admin/brands/{id}")
    public ApiResponse<Void> deactivateBrand(@PathVariable String id) {
        deactivateBrand.handle(new DeactivateBrand.Command(id));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/admin/brands/{id}/activate")
    public ApiResponse<Void> activateBrand(@PathVariable String id) {
        activateBrand.handle(new ActivateBrand.Command(id));
        return ApiResponse.ok(null);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
