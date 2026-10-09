package vn.t3nexus.catalog.presentation.product;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.t3nexus.catalog.application.product.*;
import vn.t3nexus.catalog.presentation.product.model.*;
import vn.t3nexus.lib.web.commons.response.ApiResponse;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ProductController {

    private final CreateProduct createProduct;
    private final UpdateProduct updateProduct;
    private final DeleteProduct deleteProduct;
    private final PublishProduct publishProduct;
    private final UnpublishProduct unpublishProduct;
    private final BlockProduct blockProduct;
    private final UnblockProduct unblockProduct;
    private final GetPublishedProduct getPublishedProduct;
    private final GetSellerProduct getSellerProduct;
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
                .map(a -> new CreateProduct.AttributeValue(a.templateId(), a.values(), a.isVariantDefining()))
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
        GetProduct.Result result = getSellerProduct.handle(new GetSellerProduct.Query(sellerId, id));
        return ApiResponse.ok(toProductResponse(result));
    }

    @PutMapping("/api/seller/products/{id}")
    public ApiResponse<Void> updateProduct(
            @RequestHeader("X-Seller-Id") String sellerId,
            @PathVariable String id,
            @Valid @RequestBody UpdateProductRequest request) {
        List<UpdateProduct.AttributeValueDto> attrDtos = request.attributeValues().stream()
                .map(a -> new UpdateProduct.AttributeValueDto(a.templateId(), a.values(), a.isVariantDefining()))
                .toList();
        updateProduct.handle(new UpdateProduct.Command(
                sellerId,
                id,
                request.name(),
                request.description(),
                request.warrantyMonths(),
                request.warrantyType(),
                request.warrantyCoverage(),
                attrDtos));
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/api/seller/products/{id}")
    public ApiResponse<Void> deleteProduct(@RequestHeader("X-Seller-Id") String sellerId, @PathVariable String id) {
        deleteProduct.handle(new DeleteProduct.Command(sellerId, id));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/seller/products/{id}/publish")
    public ApiResponse<Void> publishProduct(@RequestHeader("X-Seller-Id") String sellerId, @PathVariable String id) {
        publishProduct.handle(new PublishProduct.Command(sellerId, id));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/seller/products/{id}/unpublish")
    public ApiResponse<Void> unpublishProduct(@RequestHeader("X-Seller-Id") String sellerId, @PathVariable String id) {
        unpublishProduct.handle(new UnpublishProduct.Command(sellerId, id));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/seller/products/{id}/images/upload-url")
    public ApiResponse<UploadUrlResponse> getImageUploadUrl(@RequestHeader("X-Seller-Id") String sellerId, @PathVariable String id) {
        GetProductImageUploadUrl.Result result = getProductImageUploadUrl.handle(
                new GetProductImageUploadUrl.Query(sellerId, id));
        return ApiResponse.ok(new UploadUrlResponse(result.uploadUrl(), result.objectKey(), result.ttlSeconds()));
    }

    @PostMapping("/api/seller/products/{id}/images/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<String> confirmImageUpload(
            @RequestHeader("X-Seller-Id") String sellerId,
            @PathVariable String id,
            @Valid @RequestBody ConfirmImageUploadRequest request) {
        ConfirmProductImageUpload.Result result = confirmProductImageUpload.handle(
                new ConfirmProductImageUpload.Command(sellerId, id, request.objectKey()));
        return ApiResponse.ok(result.imageId());
    }

    @DeleteMapping("/api/seller/products/{id}/images/{imageId}")
    public ApiResponse<Void> removeImage(@RequestHeader("X-Seller-Id") String sellerId, @PathVariable String id,
                                     @PathVariable String imageId) {
        removeProductImage.handle(new RemoveProductImage.Command(sellerId, id, imageId));
        return ApiResponse.ok(null);
    }

    // ── Public ──────────────────────────────────────────────────────────────

    @GetMapping("/api/products/{id}")
    public ApiResponse<ProductResponse> getPublishedProduct(@PathVariable String id) {
        GetProduct.Result result = getPublishedProduct.handle(new GetPublishedProduct.Query(id));
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
                .map(a -> new ProductResponse.AttributeValueResponse(
                        a.templateId(), a.templateName(), a.templateDisplayName(),
                        a.values().stream()
                                .map(v -> new ProductResponse.AttributeValueItemResponse(v.value(), v.displayValue()))
                                .toList(),
                        a.isVariantDefining()))
                .toList();

        List<ProductResponse.ProductImageResponse> images = result.images().stream()
                .map(i -> new ProductResponse.ProductImageResponse(i.imageId(), i.objectKey(), i.displayOrder()))
                .toList();

        return new ProductResponse(
                result.id(), result.sellerId(),
                result.categoryId(), result.categoryName(),
                result.brandId(), result.brandName(),
                result.name(), result.description(), result.status(),
                warranty, attrs, images);
    }

    public record UploadUrlResponse(String uploadUrl, String objectKey, int ttlSeconds) {}
}
