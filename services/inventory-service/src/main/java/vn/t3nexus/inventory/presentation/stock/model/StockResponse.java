package vn.t3nexus.inventory.presentation.stock.model;

public record StockResponse(
        String stockId,
        String skuId,
        String productId,
        String sellerId,
        int totalQuantity,
        int reservedQuantity,
        int availableQuantity,
        boolean sellerActive,
        boolean productPublished,
        boolean adminBlocked,
        boolean sellable,
        int lowStockThreshold
) {}
