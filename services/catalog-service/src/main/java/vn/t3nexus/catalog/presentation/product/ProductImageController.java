package vn.t3nexus.catalog.presentation.product;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import vn.t3nexus.catalog.application.product.ConfirmImage;
import vn.t3nexus.catalog.application.product.ImageView;
import vn.t3nexus.catalog.application.product.RemoveImage;
import vn.t3nexus.catalog.application.product.ReorderImages;
import vn.t3nexus.catalog.application.product.RequestImageUpload;
import vn.t3nexus.catalog.application.product.UploadSlotView;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.CatalogFieldException;
import vn.t3nexus.catalog.presentation.CatalogActorResolver;
import vn.t3nexus.catalog.presentation.SellerBodyReader;
import vn.t3nexus.lib.web.commons.response.ApiResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Quản lý ảnh sản phẩm của người bán (CAP-CAT-08). Phân quyền vai USER (người bán) kiểm trong use case.
 */
@RestController
@RequiredArgsConstructor
public class ProductImageController {

    private final RequestImageUpload requestImageUpload;
    private final ConfirmImage confirmImage;
    private final ReorderImages reorderImages;
    private final RemoveImage removeImage;

    @PostMapping("/api/seller/products/{id}/images/upload-slots")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UploadSlotView> requestUploadSlot(@AuthenticationPrincipal Jwt jwt,
                                                         @PathVariable UUID id,
                                                         @RequestBody JsonNode body) {
        CatalogActor actor = CatalogActorResolver.from(jwt);
        JsonNode json = SellerBodyReader.requireObject(body);
        String contentType = SellerBodyReader.textOrNull(json, "contentType");
        if (contentType == null) {
            throw new CatalogFieldException("contentType");
        }
        long size = SellerBodyReader.requiredInteger(json, "size");

        return ApiResponse.ok(requestImageUpload.handle(new RequestImageUpload.Command(actor, id, contentType, size)));
    }

    @PostMapping("/api/seller/products/{id}/images")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ImageView> confirmImage(@AuthenticationPrincipal Jwt jwt,
                                               @PathVariable UUID id,
                                               @RequestBody JsonNode body) {
        CatalogActor actor = CatalogActorResolver.from(jwt);
        JsonNode json = SellerBodyReader.requireObject(body);
        UUID slotId = SellerBodyReader.requiredUuid(json, "slotId");

        return ApiResponse.ok(confirmImage.handle(new ConfirmImage.Command(actor, id, slotId)));
    }

    @PutMapping("/api/seller/products/{id}/images/order")
    public ApiResponse<List<ImageView>> reorderImages(@AuthenticationPrincipal Jwt jwt,
                                                      @PathVariable UUID id,
                                                      @RequestBody JsonNode body) {
        CatalogActor actor = CatalogActorResolver.from(jwt);
        JsonNode json = SellerBodyReader.requireObject(body);
        List<UUID> imageIds = readUuidList(json, "imageIds");

        return ApiResponse.ok(reorderImages.handle(new ReorderImages.Command(actor, id, imageIds)));
    }

    @DeleteMapping("/api/seller/products/{id}/images/{imageId}")
    public ApiResponse<List<ImageView>> removeImage(@AuthenticationPrincipal Jwt jwt,
                                                    @PathVariable UUID id,
                                                    @PathVariable UUID imageId) {
        CatalogActor actor = CatalogActorResolver.from(jwt);
        return ApiResponse.ok(removeImage.handle(new RemoveImage.Command(actor, id, imageId)));
    }

    private static List<UUID> readUuidList(JsonNode parent, String name) {
        if (!parent.has(name) || parent.get(name).isNull()) {
            throw new CatalogFieldException(name);
        }
        JsonNode node = parent.get(name);
        if (!node.isArray()) {
            throw new CatalogFieldException(name);
        }
        List<UUID> list = new ArrayList<>();
        for (int i = 0; i < node.size(); i++) {
            JsonNode item = node.get(i);
            if (item == null || !item.isString()) {
                throw new CatalogFieldException(name);
            }
            try {
                list.add(UUID.fromString(item.asString()));
            } catch (IllegalArgumentException e) {
                throw new CatalogFieldException(name);
            }
        }
        return List.copyOf(list);
    }
}
