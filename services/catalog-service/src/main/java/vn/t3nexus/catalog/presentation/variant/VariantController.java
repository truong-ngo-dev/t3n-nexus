package vn.t3nexus.catalog.presentation.variant;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
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

    @GetMapping("/api/products/{productId}/variants")
    public ApiResponse<List<VariantResponse>> getVariants(@PathVariable String productId) {
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
            @PathVariable String productId,
            @Valid @RequestBody AddVariantRequest request) {
        List<AddVariant.CombinationItemDto> combination = request.combination().stream()
                .map(item -> new AddVariant.CombinationItemDto(item.templateId(), item.optionId()))
                .toList();
        AddVariant.Result result = addVariant.handle(new AddVariant.Command(
                productId, combination, request.skuCode(), request.price(), request.stock()));
        return ApiResponse.ok(result.skuId());
    }

    @PutMapping("/api/seller/products/{productId}/variants/{skuId}")
    public ApiResponse<Void> updateVariant(
            @PathVariable String productId,
            @PathVariable String skuId,
            @Valid @RequestBody UpdateVariantRequest request) {
        updateVariant.handle(new UpdateVariant.Command(productId, skuId, request.price(), request.skuCode()));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/seller/products/{productId}/variants/{skuId}/deactivate")
    public ApiResponse<Void> deactivateVariant(
            @PathVariable String productId,
            @PathVariable String skuId) {
        deactivateVariant.handle(new DeactivateVariant.Command(productId, skuId));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/seller/products/{productId}/variants/{skuId}/activate")
    public ApiResponse<Void> activateVariant(
            @PathVariable String productId,
            @PathVariable String skuId) {
        activateVariant.handle(new ActivateVariant.Command(productId, skuId));
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
                dto.skuCode(), dto.price(), dto.stock(), dto.status(), images);
    }
}
