package vn.t3nexus.inventory.presentation.stock;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.t3nexus.inventory.application.stock.GetStock;
import vn.t3nexus.inventory.application.stock.GetStockList;
import vn.t3nexus.inventory.application.stock.SetStockQuantity;
import vn.t3nexus.inventory.presentation.stock.model.SetStockQuantityRequest;
import vn.t3nexus.inventory.presentation.stock.model.StockResponse;
import vn.t3nexus.lib.web.commons.response.ApiResponse;

@RestController
@RequiredArgsConstructor
public class StockController {

    private final SetStockQuantity setStockQuantity;
    private final GetStock getStock;
    private final GetStockList getStockList;

    @PutMapping("/api/seller/inventory/stocks/{skuId}/quantity")
    public ApiResponse<Void> setQuantity(
            @RequestHeader("X-Seller-Id") String sellerId,
            @PathVariable String skuId,
            @Valid @RequestBody SetStockQuantityRequest request) {
        setStockQuantity.handle(new SetStockQuantity.Command(
                skuId, request.quantity(), request.lowStockThreshold(), sellerId));
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/seller/inventory/stocks")
    public ApiResponse<GetStockList.Result> listStocks(
            @RequestHeader("X-Seller-Id") String sellerId,
            @RequestParam(required = false) String productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(getStockList.handle(new GetStockList.Query(sellerId, productId, page, size)));
    }

    @GetMapping("/api/seller/inventory/stocks/{skuId}")
    public ApiResponse<StockResponse> getStock(
            @RequestHeader("X-Seller-Id") String sellerId,
            @PathVariable String skuId) {
        GetStock.Result result = getStock.handle(new GetStock.Query(skuId, sellerId));
        return ApiResponse.ok(new StockResponse(
                result.stockId(), result.skuId(), result.productId(), result.sellerId(),
                result.totalQuantity(), result.reservedQuantity(), result.availableQuantity(),
                result.sellerActive(), result.productPublished(), result.adminBlocked(), result.sellable(),
                result.lowStockThreshold()));
    }
}
