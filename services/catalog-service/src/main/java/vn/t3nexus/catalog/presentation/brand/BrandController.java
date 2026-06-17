package vn.t3nexus.catalog.presentation.brand;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.t3nexus.catalog.application.brand.*;
import vn.t3nexus.catalog.presentation.brand.model.BrandResponse;
import vn.t3nexus.catalog.presentation.brand.model.CreateBrandRequest;
import vn.t3nexus.catalog.presentation.brand.model.UpdateBrandRequest;
import vn.t3nexus.lib.web.commons.response.ApiResponse;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BrandController {

    private final CreateBrand createBrand;
    private final UpdateBrand updateBrand;
    private final DeactivateBrand deactivateBrand;
    private final ListActiveBrands listActiveBrands;

    @GetMapping("/api/brands")
    public ApiResponse<List<BrandResponse>> listBrands() {
        ListActiveBrands.Result result = listActiveBrands.handle(new ListActiveBrands.Query());
        List<BrandResponse> data = result.brands().stream()
                .map(b -> new BrandResponse(b.id(), b.name(), b.slug()))
                .toList();
        return ApiResponse.ok(data);
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
}
