package vn.t3nexus.catalog.presentation.variant;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import vn.t3nexus.catalog.application.variant.ActivateVariant;
import vn.t3nexus.catalog.application.variant.AddVariant;
import vn.t3nexus.catalog.application.variant.DeactivateVariant;
import vn.t3nexus.catalog.application.variant.UpdateVariant;
import vn.t3nexus.catalog.application.variant.VariantView;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.presentation.CatalogActorResolver;
import vn.t3nexus.catalog.presentation.SellerBodyReader;
import vn.t3nexus.lib.web.commons.response.ApiResponse;

import java.util.UUID;

/**
 * Biến thể của sản phẩm của người bán (CAP-CAT-05): thêm, đổi giá và SKU, ngừng, bật lại. Không có đường sửa tổ hợp hay
 * xóa riêng một biến thể (INV-CAT-41, 42). Vai {@code USER} và quyền chủ kiểm trong use case.
 */
@RestController
@RequiredArgsConstructor
public class VariantController {

    private final AddVariant addVariant;
    private final UpdateVariant updateVariant;
    private final DeactivateVariant deactivateVariant;
    private final ActivateVariant activateVariant;

    @PostMapping("/api/seller/products/{id}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VariantView> add(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                        @RequestBody JsonNode body) {
        CatalogActor actor = CatalogActorResolver.from(jwt);
        JsonNode json = SellerBodyReader.requireObject(body);
        return ApiResponse.ok(addVariant.handle(new AddVariant.Command(actor, id,
                SellerBodyReader.combination(json, "combination"), SellerBodyReader.requiredInteger(json, "price"),
                SellerBodyReader.textOrNull(json, "sku"))));
    }

    @PatchMapping("/api/seller/products/{id}/variants/{variantId}")
    public ApiResponse<VariantView> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                           @PathVariable UUID variantId, @RequestBody JsonNode body) {
        CatalogActor actor = CatalogActorResolver.from(jwt);
        JsonNode json = SellerBodyReader.requireObject(body);
        return ApiResponse.ok(updateVariant.handle(new UpdateVariant.Command(actor, id, variantId,
                SellerBodyReader.integer(json, "price"), SellerBodyReader.text(json, "sku"))));
    }

    @PostMapping("/api/seller/products/{id}/variants/{variantId}/deactivate")
    public ApiResponse<VariantView> deactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                               @PathVariable UUID variantId) {
        return ApiResponse.ok(deactivateVariant.handle(
                new DeactivateVariant.Command(CatalogActorResolver.from(jwt), id, variantId)));
    }

    @PostMapping("/api/seller/products/{id}/variants/{variantId}/activate")
    public ApiResponse<VariantView> activate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                             @PathVariable UUID variantId) {
        return ApiResponse.ok(activateVariant.handle(
                new ActivateVariant.Command(CatalogActorResolver.from(jwt), id, variantId)));
    }
}
