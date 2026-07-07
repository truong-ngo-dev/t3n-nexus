package vn.t3nexus.inventory.presentation.limitedoffer;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.t3nexus.inventory.application.limitedoffer.ActivateLimitedOffer;
import vn.t3nexus.inventory.application.limitedoffer.DeactivateLimitedOffer;
import vn.t3nexus.inventory.presentation.limitedoffer.model.ActivateLimitedOfferRequest;
import vn.t3nexus.lib.web.commons.response.ApiResponse;

@RestController
@RequiredArgsConstructor
public class LimitedOfferController {

    private final ActivateLimitedOffer activateLimitedOffer;
    private final DeactivateLimitedOffer deactivateLimitedOffer;

    @PostMapping("/api/seller/inventory/stocks/{skuId}/limited-offer")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ActivateLimitedOffer.Result> activate(
            @RequestHeader("X-Seller-Id") String sellerId,
            @PathVariable String skuId,
            @Valid @RequestBody ActivateLimitedOfferRequest request) {
        ActivateLimitedOffer.Result result = activateLimitedOffer.handle(
                new ActivateLimitedOffer.Command(skuId, sellerId, request.maxQuantity(), request.windowSeconds()));
        return ApiResponse.ok(result);
    }

    @DeleteMapping("/api/seller/inventory/stocks/{skuId}/limited-offer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(
            @RequestHeader("X-Seller-Id") String sellerId,
            @PathVariable String skuId) {
        deactivateLimitedOffer.handle(new DeactivateLimitedOffer.Command(skuId, sellerId));
    }
}
