package vn.t3nexus.catalog.presentation.product;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import vn.t3nexus.catalog.application.product.CreateProduct;
import vn.t3nexus.catalog.application.product.DeleteDraft;
import vn.t3nexus.catalog.application.product.GetMyProduct;
import vn.t3nexus.catalog.application.product.ListMyProducts;
import vn.t3nexus.catalog.application.product.PublishProduct;
import vn.t3nexus.catalog.application.product.UnpublishProduct;
import vn.t3nexus.catalog.application.product.UpdateProduct;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.product.AttributeInput;
import vn.t3nexus.catalog.domain.product.ProductStatus;
import vn.t3nexus.catalog.presentation.CatalogActorResolver;
import vn.t3nexus.catalog.presentation.SellerBodyReader;
import vn.t3nexus.catalog.presentation.product.model.ProductResponse;
import vn.t3nexus.lib.web.commons.response.ApiResponse;

import java.util.List;
import java.util.UUID;

/**
 * Sản phẩm của người bán (CAP-CAT-04, CAP-CAT-09): tạo nháp, xem, sửa, đăng, gỡ, xóa nháp.
 * Vai {@code USER} và quyền chủ kiểm trong use case; chủ lấy từ token.
 */
@RestController
@RequiredArgsConstructor
public class ProductController {

    /** Trần kích thước trang; vượt thì cắt về trần. */
    private static final int MAX_PAGE_SIZE = 50;

    private final CreateProduct createProduct;
    private final UpdateProduct updateProduct;
    private final GetMyProduct getMyProduct;
    private final ListMyProducts listMyProducts;
    private final PublishProduct publishProduct;
    private final UnpublishProduct unpublishProduct;
    private final DeleteDraft deleteDraft;

    @PostMapping("/api/seller/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> create(@AuthenticationPrincipal Jwt jwt, @RequestBody JsonNode body) {
        CatalogActor actor = CatalogActorResolver.from(jwt);
        JsonNode json = SellerBodyReader.requireObject(body);
        List<AttributeInput> attributes = SellerBodyReader.attributes(json, "attributes").value();
        return ok(createProduct.handle(new CreateProduct.Command(actor,
                SellerBodyReader.textOrNull(json, "title"), SellerBodyReader.textOrNull(json, "description"),
                SellerBodyReader.requiredUuid(json, "categoryId"), SellerBodyReader.uuid(json, "brandId").value(),
                attributes == null ? List.of() : attributes)));
    }

    @GetMapping("/api/seller/products")
    public ApiResponse<ListMyProducts.Result> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(listMyProducts.handle(new ListMyProducts.Query(CatalogActorResolver.from(jwt), status,
                Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE))));
    }

    @GetMapping("/api/seller/products/{id}")
    public ApiResponse<ProductResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ok(getMyProduct.handle(new GetMyProduct.Query(CatalogActorResolver.from(jwt), id)));
    }

    @PatchMapping("/api/seller/products/{id}")
    public ApiResponse<ProductResponse> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                               @RequestBody JsonNode body) {
        CatalogActor actor = CatalogActorResolver.from(jwt);
        JsonNode json = SellerBodyReader.requireObject(body);
        return ok(updateProduct.handle(new UpdateProduct.Command(actor, id,
                SellerBodyReader.text(json, "title"), SellerBodyReader.text(json, "description"),
                SellerBodyReader.uuid(json, "brandId"), SellerBodyReader.attributes(json, "attributes"))));
    }

    @PostMapping("/api/seller/products/{id}/publish")
    public ApiResponse<ProductResponse> publish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        CatalogActor actor = CatalogActorResolver.from(jwt);
        return ok(publishProduct.handle(new PublishProduct.Command(actor, id)));
    }

    @PostMapping("/api/seller/products/{id}/unpublish")
    public ApiResponse<ProductResponse> unpublish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        CatalogActor actor = CatalogActorResolver.from(jwt);
        return ok(unpublishProduct.handle(new UnpublishProduct.Command(actor, id)));
    }

    @DeleteMapping("/api/seller/products/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        CatalogActor actor = CatalogActorResolver.from(jwt);
        deleteDraft.handle(new DeleteDraft.Command(actor, id));
        return ApiResponse.ok(null);
    }

    private static ApiResponse<ProductResponse> ok(vn.t3nexus.catalog.application.product.ProductView view) {
        return ApiResponse.ok(ProductResponse.from(view));
    }
}
