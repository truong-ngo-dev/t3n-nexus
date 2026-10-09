package vn.t3nexus.catalog.presentation.variant;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.t3nexus.catalog.application.product.GetPublishedProduct;
import vn.t3nexus.catalog.application.variant.*;
import vn.t3nexus.catalog.presentation.variant.model.AddVariantRequest;
import vn.t3nexus.catalog.presentation.variant.model.UpdateVariantRequest;
import vn.t3nexus.catalog.presentation.variant.model.VariantResponse;
import vn.t3nexus.lib.web.commons.response.ApiResponse;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class VariantController {

    private final AddVariant addVariant;
    private final UpdateVariant updateVariant;
    private final DeactivateVariant deactivateVariant;
    private final ActivateVariant activateVariant;
    private final GetProductVariants getProductVariants;
    private final GetPublishedProduct getPublishedProduct;

    @GetMapping("/api/products/{productId}/variants")
    public ApiResponse<List<VariantResponse>> getVariants(@PathVariable String productId) {
        // RM-CAT-03 cùng điều kiện RM-CAT-02 — sản phẩm không được xem công khai thì 404 (INV-CAT-044).
        getPublishedProduct.handle(new GetPublishedProduct.Query(productId));
        List<VariantResponse> variants = getProductVariants.handle(
                new GetProductVariants.Query(productId))
                .variants().stream()
                .map(this::toVariantResponse)
                .toList();
        return ApiResponse.ok(variants);
    }

    @PostMapping("/api/seller/products/{productId}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<String> addVariant(
            @RequestHeader("X-Seller-Id") String sellerId,
            @PathVariable String productId,
            @Valid @RequestBody AddVariantRequest request) {
        List<AddVariant.CombinationItemDto> combination = request.combination().stream()
                .map(item -> new AddVariant.CombinationItemDto(item.templateId(), item.optionId()))
                .toList();
        AddVariant.Result result = addVariant.handle(new AddVariant.Command(
                sellerId, productId, combination, request.skuCode(), request.price()));
        return ApiResponse.ok(result.skuId());
    }

    @PutMapping("/api/seller/products/{productId}/variants/{skuId}")
    public ApiResponse<Void> updateVariant(
            @RequestHeader("X-Seller-Id") String sellerId,
            @PathVariable String productId,
            @PathVariable String skuId,
            @Valid @RequestBody UpdateVariantRequest request) {
        updateVariant.handle(new UpdateVariant.Command(sellerId, productId, skuId, request.price(), request.skuCode()));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/seller/products/{productId}/variants/{skuId}/deactivate")
    public ApiResponse<Void> deactivateVariant(
            @RequestHeader("X-Seller-Id") String sellerId,
            @PathVariable String productId,
            @PathVariable String skuId) {
        deactivateVariant.handle(new DeactivateVariant.Command(sellerId, productId, skuId));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/seller/products/{productId}/variants/{skuId}/activate")
    public ApiResponse<Void> activateVariant(
            @RequestHeader("X-Seller-Id") String sellerId,
            @PathVariable String productId,
            @PathVariable String skuId) {
        activateVariant.handle(new ActivateVariant.Command(sellerId, productId, skuId));
        return ApiResponse.ok(null);
    }

    private VariantResponse toVariantResponse(GetProductVariants.VariantDto dto) {
        List<VariantResponse.CombinationItemResponse> combination = dto.combination().stream()
                .map(item -> new VariantResponse.CombinationItemResponse(item.templateId(), item.optionId()))
                .toList();
        List<VariantResponse.SkuImageResponse> images = dto.images().stream()
                .map(img -> new VariantResponse.SkuImageResponse(img.imageId(), img.objectKey(), img.displayOrder()))
                .toList();
        return new VariantResponse(
                dto.skuId(), dto.productId(), combination,
                dto.skuCode(), dto.price(), dto.status(), images);
    }
}
