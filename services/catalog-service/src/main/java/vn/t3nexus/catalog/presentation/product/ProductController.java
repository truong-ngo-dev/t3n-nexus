package vn.t3nexus.catalog.presentation.product;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.t3nexus.catalog.application.product.*;
import vn.t3nexus.catalog.presentation.product.model.*;
import vn.t3nexus.lib.web.commons.response.ApiResponse;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ProductController {

    private final CreateProduct createProduct;
    private final UpdateProduct updateProduct;
    private final PublishProduct publishProduct;
    private final UnpublishProduct unpublishProduct;
    private final BlockProduct blockProduct;
    private final UnblockProduct unblockProduct;
    private final GetProduct getProduct;
    private final ListSellerProducts listSellerProducts;
    private final RemoveProductImage removeProductImage;
    private final GetProductImageUploadUrl getProductImageUploadUrl;
    private final ConfirmProductImageUpload confirmProductImageUpload;

    // ── Seller ──────────────────────────────────────────────────────────────

    @PostMapping("/api/seller/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<String> createProduct(
            @RequestHeader("X-Seller-Id") String sellerId,
            @Valid @RequestBody CreateProductRequest request) {
        List<CreateProduct.AttributeValue> attrs = request.attributeValues().stream()
                .map(a -> new CreateProduct.AttributeValue(a.templateId(), a.value()))
                .toList();
        CreateProduct.Result result = createProduct.handle(new CreateProduct.Command(
                sellerId,
                request.categoryId(),
                request.brandId(),
                request.name(),
                request.description(),
                request.warrantyMonths(),
                request.warrantyType(),
                request.warrantyCoverage(),
                attrs));
        return ApiResponse.ok(result.id());
    }

    @GetMapping("/api/seller/products")
    public ApiResponse<ProductSummaryResponse.PagedResponse> listProducts(
            @RequestHeader("X-Seller-Id") String sellerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ListSellerProducts.Result result = listSellerProducts.handle(
                new ListSellerProducts.Query(sellerId, page, size));
        List<ProductSummaryResponse> items = result.items().stream()
                .map(dto -> new ProductSummaryResponse(
                        dto.id(), dto.name(), dto.status(),
                        dto.categoryId(), dto.brandId(),
                        dto.thumbnailObjectKey(), dto.createdAt()))
                .toList();
        return ApiResponse.ok(new ProductSummaryResponse.PagedResponse(items, result.total(), page, size));
    }

    @GetMapping("/api/seller/products/{id}")
    public ApiResponse<ProductResponse> getSellerProduct(
            @RequestHeader("X-Seller-Id") String sellerId,
            @PathVariable String id) {
        GetProduct.Result result = getProduct.handle(new GetProduct.Query(id));
        return ApiResponse.ok(toProductResponse(result));
    }

    @PutMapping("/api/seller/products/{id}")
    public ApiResponse<Void> updateProduct(
            @PathVariable String id,
            @Valid @RequestBody UpdateProductRequest request) {
        List<UpdateProduct.AttributeValueDto> attrDtos = request.attributeValues().stream()
                .map(a -> new UpdateProduct.AttributeValueDto(a.templateId(), a.value()))
                .toList();
        updateProduct.handle(new UpdateProduct.Command(
                id,
                request.name(),
                request.description(),
                request.warrantyMonths(),
                request.warrantyType(),
                request.warrantyCoverage(),
                attrDtos));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/seller/products/{id}/publish")
    public ApiResponse<Void> publishProduct(@PathVariable String id) {
        publishProduct.handle(new PublishProduct.Command(id));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/seller/products/{id}/unpublish")
    public ApiResponse<Void> unpublishProduct(@PathVariable String id) {
        unpublishProduct.handle(new UnpublishProduct.Command(id));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/seller/products/{id}/images/upload-url")
    public ApiResponse<UploadUrlResponse> getImageUploadUrl(@PathVariable String id) {
        GetProductImageUploadUrl.Result result = getProductImageUploadUrl.handle(
                new GetProductImageUploadUrl.Query(id));
        return ApiResponse.ok(new UploadUrlResponse(result.uploadUrl(), result.objectKey(), result.ttlSeconds()));
    }

    @PostMapping("/api/seller/products/{id}/images/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<String> confirmImageUpload(
            @PathVariable String id,
            @Valid @RequestBody ConfirmImageUploadRequest request) {
        ConfirmProductImageUpload.Result result = confirmProductImageUpload.handle(
                new ConfirmProductImageUpload.Command(id, request.objectKey()));
        return ApiResponse.ok(result.imageId());
    }

    @DeleteMapping("/api/seller/products/{id}/images/{imageId}")
    public ApiResponse<Void> removeImage(@PathVariable String id, @PathVariable String imageId) {
        removeProductImage.handle(new RemoveProductImage.Command(id, imageId));
        return ApiResponse.ok(null);
    }

    // ── Public ──────────────────────────────────────────────────────────────

    @GetMapping("/api/products/{id}")
    public ApiResponse<ProductResponse> getPublishedProduct(@PathVariable String id) {
        GetProduct.Result result = getProduct.handle(new GetProduct.Query(id));
        if (!"PUBLISHED".equals(result.status())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return ApiResponse.ok(toProductResponse(result));
    }

    // ── Admin ────────────────────────────────────────────────────────────────

    @PostMapping("/api/admin/products/{id}/block")
    public ApiResponse<Void> blockProduct(
            @PathVariable String id,
            @Valid @RequestBody BlockProductRequest request) {
        blockProduct.handle(new BlockProduct.Command(id, request.reason()));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/admin/products/{id}/unblock")
    public ApiResponse<Void> unblockProduct(@PathVariable String id) {
        unblockProduct.handle(new UnblockProduct.Command(id));
        return ApiResponse.ok(null);
    }

    // ── Mapper ──────────────────────────────────────────────────────────────

    private ProductResponse toProductResponse(GetProduct.Result result) {
        ProductResponse.WarrantyInfoResponse warranty = result.warrantyInfo() == null ? null
                : new ProductResponse.WarrantyInfoResponse(
                        result.warrantyInfo().months(),
                        result.warrantyInfo().type(),
                        result.warrantyInfo().coverage());

        List<ProductResponse.AttributeValueResponse> attrs = result.attributeValues().stream()
                .map(a -> new ProductResponse.AttributeValueResponse(a.templateId(), a.value()))
                .toList();

        List<ProductResponse.ProductImageResponse> images = result.images().stream()
                .map(i -> new ProductResponse.ProductImageResponse(i.imageId(), i.objectKey(), i.displayOrder()))
                .toList();

        return new ProductResponse(
                result.id(), result.sellerId(), result.categoryId(), result.brandId(),
                result.name(), result.description(), result.status(),
                warranty, attrs, images);
    }

    public record UploadUrlResponse(String uploadUrl, String objectKey, int ttlSeconds) {}
}
